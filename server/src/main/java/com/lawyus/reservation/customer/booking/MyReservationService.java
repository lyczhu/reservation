package com.lawyus.reservation.customer.booking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.common.security.PhoneCrypto;
import com.lawyus.reservation.customer.booking.dto.ReservationDetailVO;
import com.lawyus.reservation.customer.booking.dto.ReservationListVO;
import com.lawyus.reservation.customer.booking.dto.ReservationRescheduleRequest;
import com.lawyus.reservation.integration.wx.SubscribeNotifier;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.ReservationLog;
import com.lawyus.reservation.kernel.reservation.ReservationRuleSnapshot;
import com.lawyus.reservation.kernel.reservation.ReservationTexts;
import com.lawyus.reservation.kernel.reservation.ReservationTimelineVO;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationLogMapper;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import com.lawyus.reservation.kernel.slot.SlotCalculator;

import tools.jackson.core.type.TypeReference;

/**
 * 我的预约：列表、详情、取消与改期。取消/改期时限读预约创建时的规则快照；
 * 改期复用提交时的时段校验与技师行锁，先释放原时段再占用新时段，全程留痕。
 */
@Service
public class MyReservationService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("MM月dd日");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");

    private final BookingDataService data;
    private final ReservationMapper reservationMapper;
    private final ReservationLogMapper reservationLogMapper;
    private final TechnicianMapper technicianMapper;
    private final PhoneCrypto phoneCrypto;
    private final JsonSupport jsonSupport;
    private final SubscribeNotifier subscribeNotifier;

    public MyReservationService(BookingDataService data,
                                ReservationMapper reservationMapper,
                                ReservationLogMapper reservationLogMapper,
                                TechnicianMapper technicianMapper,
                                PhoneCrypto phoneCrypto,
                                JsonSupport jsonSupport,
                                SubscribeNotifier subscribeNotifier) {
        this.data = data;
        this.reservationMapper = reservationMapper;
        this.reservationLogMapper = reservationLogMapper;
        this.technicianMapper = technicianMapper;
        this.phoneCrypto = phoneCrypto;
        this.jsonSupport = jsonSupport;
        this.subscribeNotifier = subscribeNotifier;
    }

    public List<ReservationListVO> list(long userId, Integer status) {
        if (status != null && (status < Reservation.STATUS_WAIT_CONFIRM || status > Reservation.STATUS_NO_SHOW)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 仅支持 1-5");
        }
        List<Reservation> reservations = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .eq(status != null, Reservation::getStatus, status)
                .orderByDesc(Reservation::getDate)
                .orderByDesc(Reservation::getStartTime)
                .orderByDesc(Reservation::getId));
        if (reservations.isEmpty()) {
            return List.of();
        }
        Set<Long> serviceIds = reservations.stream().map(Reservation::getServiceId).collect(Collectors.toSet());
        Set<Long> technicianIds = reservations.stream().map(Reservation::getTechnicianId).collect(Collectors.toSet());
        Map<Long, ServiceItem> services = data.services(serviceIds);
        Map<Long, Technician> technicians = data.technicians(technicianIds);
        return reservations.stream().map(reservation -> {
            ServiceItem service = services.get(reservation.getServiceId());
            Technician technician = technicians.get(reservation.getTechnicianId());
            OperationFlags flags = operationFlags(reservation);
            return new ReservationListVO(reservation.getId(), reservation.getNo(), reservation.getStatus(),
                    statusText(reservation.getStatus()),
                    service == null ? "" : service.getName(),
                    service == null ? null : service.getCover(),
                    technician == null ? "" : technician.getName(),
                    reservation.getDate(),
                    reservation.getStartTime().format(HH_MM),
                    reservation.getEndTime().format(HH_MM),
                    service == null ? 0 : service.getPriceCents(),
                    flags.canCancel(), flags.canReschedule());
        }).toList();
    }

    public ReservationDetailVO detail(long userId, long id) {
        return toDetail(requireOwned(userId, id));
    }

    @Transactional
    public ReservationDetailVO cancel(long userId, long id, String reason) {
        Reservation reservation = requireOwned(userId, id);
        requireActive(reservation);
        ReservationRuleSnapshot snapshot = snapshotOf(reservation);
        if (startAt(reservation).isBefore(LocalDateTime.now().plusHours(snapshot.cancelDeadlineHours()))) {
            throw new BusinessException(ErrorCode.DEADLINE_EXCEEDED, "已过免费取消时限，请联系商家");
        }
        String cancelReason = reason == null || reason.isBlank() ? "顾客取消" : reason.trim();
        if (cancelReason.length() > 50) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "取消原因不能超过 50 字");
        }
        Reservation update = new Reservation();
        update.setStatus(Reservation.STATUS_CANCELLED);
        update.setCancelledAt(LocalDateTime.now());
        update.setCancelReason(cancelReason);
        int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                .eq(Reservation::getId, id)
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, Reservation.STATUS_WAIT_CONFIRM, Reservation.STATUS_WAIT_SERVICE));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可取消");
        }
        writeLog(id, ReservationLog.ACTION_CANCEL, ReservationLog.ROLE_USER, userId, cancelReason);
        Reservation updated = requireOwned(userId, id);
        subscribeNotifier.reservationChanged(updated, "预约已取消", cancelReason);
        return toDetail(updated);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationDetailVO reschedule(long userId, long id, ReservationRescheduleRequest request) {
        Reservation reservation = requireOwned(userId, id);
        requireActive(reservation);
        ReservationRuleSnapshot snapshot = snapshotOf(reservation);
        if (startAt(reservation).isBefore(LocalDateTime.now().plusHours(snapshot.rescheduleDeadlineHours()))) {
            throw new BusinessException(ErrorCode.DEADLINE_EXCEEDED, "已过改期时限，请联系商家");
        }
        ServiceItem service = data.service(reservation.getServiceId());
        if (service == null || service.getStatus() != ServiceItem.STATUS_ON) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "服务已下架，请联系商家");
        }
        Technician technician = data.technician(reservation.getTechnicianId());
        if (technician == null || technician.getStatus() != Technician.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.RESOURCE_UNAVAILABLE, "技师不可约，请联系商家");
        }
        RuleConfig rule = data.rules();
        BookingDataService.validateBookableDate(rule, request.date());
        LocalTime start = parseStartTime(request.startTime());
        int duration = (int) Duration.between(reservation.getStartTime(), reservation.getEndTime()).toMinutes();
        LocalTime end = start.plusMinutes(duration);
        List<TimeRange> businessHours = data.businessHours(request.date());
        validateOnGrid(businessHours, start, end, rule.getSlotGranularity());

        technicianMapper.lockById(technician.getId());
        List<Reservation> active = reservationMapper.selectActiveByTechnicianAndDate(technician.getId(), request.date())
                .stream()
                .filter(other -> !other.getId().equals(reservation.getId()))
                .toList();
        List<TimeRange> shiftIntervals = data.shiftIntervals(List.of(technician.getId()), request.date())
                .getOrDefault(technician.getId(), List.of());
        if (!available(businessHours, shiftIntervals, active, technician.getId(), start, end, rule, request.date())) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "目标时段已被约满，请重新选择");
        }

        Reservation update = new Reservation();
        update.setDate(request.date());
        update.setStartTime(start);
        update.setEndTime(end);
        try {
            int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, id)
                    .eq(Reservation::getUserId, userId)
                    .in(Reservation::getStatus, Reservation.STATUS_WAIT_CONFIRM, Reservation.STATUS_WAIT_SERVICE));
            if (rows == 0) {
                throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可改期");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "目标时段已被约满，请重新选择");
        }
        writeLog(id, ReservationLog.ACTION_RESCHEDULE, ReservationLog.ROLE_USER, userId,
                slotText(reservation.getDate(), reservation.getStartTime(), reservation.getEndTime())
                        + " → " + slotText(request.date(), start, end));
        Reservation updated = requireOwned(userId, id);
        subscribeNotifier.reservationChanged(updated, "预约已改期", request.date().format(TIME_LABEL)
                + " " + start.format(HH_MM) + "-" + end.format(HH_MM));
        return toDetail(updated);
    }

    private Reservation requireOwned(long userId, long id) {
        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getId, id)
                .eq(Reservation::getUserId, userId));
        if (reservation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        return reservation;
    }

    private void requireActive(Reservation reservation) {
        if (!isActive(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可操作");
        }
    }

    private boolean isActive(int status) {
        return status == Reservation.STATUS_WAIT_CONFIRM || status == Reservation.STATUS_WAIT_SERVICE;
    }

    private OperationFlags operationFlags(Reservation reservation) {
        if (!isActive(reservation.getStatus())) {
            return new OperationFlags(false, false);
        }
        ReservationRuleSnapshot snapshot = snapshotOf(reservation);
        LocalDateTime start = startAt(reservation);
        LocalDateTime now = LocalDateTime.now();
        return new OperationFlags(
                !start.isBefore(now.plusHours(snapshot.cancelDeadlineHours())),
                !start.isBefore(now.plusHours(snapshot.rescheduleDeadlineHours())));
    }

    private void validateOnGrid(List<TimeRange> businessHours, LocalTime start, LocalTime end, int granularity) {
        for (TimeRange window : businessHours) {
            LocalTime open = LocalTime.parse(window.getStart());
            LocalTime close = LocalTime.parse(window.getEnd());
            if (!start.isBefore(open) && !end.isAfter(close)
                    && Duration.between(open, start).toMinutes() % granularity == 0) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.PARAM_INVALID, "起始时间不在可选范围内");
    }

    private boolean available(List<TimeRange> businessHours, List<TimeRange> shiftIntervals,
                              List<Reservation> active, long technicianId, LocalTime start, LocalTime end,
                              RuleConfig rule, LocalDate date) {
        if (!SlotCalculator.covers(businessHours, start, end) || !SlotCalculator.covers(shiftIntervals, start, end)) {
            return false;
        }
        if (LocalDateTime.of(date, start).isBefore(LocalDateTime.now().plusMinutes(rule.getMinLeadMinutes()))) {
            return false;
        }
        List<SlotCalculator.BusyRange> busy = active.stream()
                .filter(other -> other.getTechnicianId() != null && other.getTechnicianId() == technicianId)
                .map(other -> new SlotCalculator.BusyRange(other.getStartTime(), other.getEndTime()))
                .toList();
        return !SlotCalculator.conflicts(busy, start, end, rule.getBufferMinutes());
    }

    private ReservationDetailVO toDetail(Reservation reservation) {
        ServiceItem service = data.service(reservation.getServiceId());
        Technician technician = data.technician(reservation.getTechnicianId());
        OperationFlags flags = operationFlags(reservation);
        boolean showVerifyCode = reservation.getStatus() == Reservation.STATUS_WAIT_SERVICE;
        int duration = (int) Duration.between(reservation.getStartTime(), reservation.getEndTime()).toMinutes();
        String contactPhone = reservation.getContactPhone() == null
                ? null : PhoneCrypto.mask(phoneCrypto.decrypt(reservation.getContactPhone()));
        return new ReservationDetailVO(
                reservation.getId(),
                reservation.getNo(),
                reservation.getStatus(),
                statusText(reservation.getStatus()),
                reservation.getServiceId(),
                service == null ? "" : service.getName(),
                service == null ? null : service.getCover(),
                reservation.getTechnicianId(),
                technician == null ? "" : technician.getName(),
                technician == null ? null : technician.getAvatar(),
                reservation.getDate(),
                reservation.getStartTime().format(HH_MM),
                reservation.getEndTime().format(HH_MM),
                duration,
                service == null ? 0 : service.getPriceCents(),
                reservation.getContactName(),
                contactPhone,
                reservation.getRemark(),
                showVerifyCode ? reservation.getVerifyCode() : null,
                showVerifyCode ? reservation.getId() + ":" + reservation.getVerifyCode() : null,
                reservation.getCancelReason(),
                reservation.getCreatedAt(),
                flags.canCancel(),
                flags.canReschedule(),
                timeline(reservation.getId()));
    }

    private List<ReservationTimelineVO> timeline(long reservationId) {
        return reservationLogMapper.selectList(new LambdaQueryWrapper<ReservationLog>()
                        .eq(ReservationLog::getReservationId, reservationId)
                        .orderByAsc(ReservationLog::getCreatedAt)
                        .orderByAsc(ReservationLog::getId)).stream()
                .map(log -> new ReservationTimelineVO(log.getAction(), actionText(log.getAction()),
                        log.getOperatorRole(), log.getDetail(), log.getCreatedAt()))
                .toList();
    }

    private ReservationRuleSnapshot snapshotOf(Reservation reservation) {
        ReservationRuleSnapshot snapshot = jsonSupport.read(reservation.getRuleSnapshot(), new TypeReference<>() {
        });
        if (snapshot != null) {
            return snapshot;
        }
        RuleConfig rule = data.rules();
        return new ReservationRuleSnapshot(rule.getSlotGranularity(), rule.getBookableDays(), rule.getMinLeadMinutes(),
                rule.getCancelDeadlineHours(), rule.getRescheduleDeadlineHours(), rule.getNeedConfirm(),
                rule.getMaxConcurrent(), rule.getBufferMinutes(), rule.getReminderHours());
    }

    private String slotText(LocalDate date, LocalTime start, LocalTime end) {
        return date.format(TIME_LABEL) + " " + start.format(HH_MM) + "-" + end.format(HH_MM);
    }

    private void writeLog(long reservationId, String action, int operatorRole, long operatorId, String detail) {
        ReservationLog log = new ReservationLog();
        log.setReservationId(reservationId);
        log.setAction(action);
        log.setOperatorRole(operatorRole);
        log.setOperatorId(operatorId);
        log.setDetail(detail);
        reservationLogMapper.insert(log);
    }

    private LocalDateTime startAt(Reservation reservation) {
        return LocalDateTime.of(reservation.getDate(), reservation.getStartTime());
    }

    private LocalTime parseStartTime(String value) {
        if (!TIME_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "startTime 格式必须为 HH:mm");
        }
        return LocalTime.parse(value);
    }

    private String statusText(int status) {
        return ReservationTexts.status(status);
    }

    private String actionText(String action) {
        return ReservationTexts.action(action);
    }

    private record OperationFlags(boolean canCancel, boolean canReschedule) {
    }
}
