package com.lawyus.reservation.beauty.reservation;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.reservation.dto.ProxyReservationRequest;
import com.lawyus.reservation.beauty.reservation.dto.StaffRescheduleRequest;
import com.lawyus.reservation.beauty.reservation.dto.StaffReservationDetailVO;
import com.lawyus.reservation.beauty.reservation.dto.StaffReservationListVO;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.PhoneCrypto;
import com.lawyus.reservation.customer.booking.BookingDataService;
import com.lawyus.reservation.customer.booking.BookingService;
import com.lawyus.reservation.integration.wx.SubscribeNotifier;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.ReservationLog;
import com.lawyus.reservation.kernel.reservation.ReservationTexts;
import com.lawyus.reservation.kernel.reservation.ReservationTimelineVO;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationLogMapper;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import com.lawyus.reservation.kernel.slot.SlotCalculator;

/**
 * 商家端预约管理：列表/详情、确认、拒绝、取消、改期、未到店与代客预约。
 * 技师角色只能看到本人预约，写操作与完整手机号查看仅店长可执行；全部操作写 reservation_log。
 */
@Service
public class StaffReservationService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_LABEL = DateTimeFormatter.ofPattern("MM月dd日");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int MAX_QUERY_DAYS = 30;

    private final BookingDataService data;
    private final BookingService bookingService;
    private final ReservationMapper reservationMapper;
    private final ReservationLogMapper reservationLogMapper;
    private final TechnicianMapper technicianMapper;
    private final PhoneCrypto phoneCrypto;
    private final AuditService auditService;
    private final SubscribeNotifier subscribeNotifier;

    public StaffReservationService(BookingDataService data,
                                   BookingService bookingService,
                                   ReservationMapper reservationMapper,
                                   ReservationLogMapper reservationLogMapper,
                                   TechnicianMapper technicianMapper,
                                   PhoneCrypto phoneCrypto,
                                   AuditService auditService,
                                   SubscribeNotifier subscribeNotifier) {
        this.data = data;
        this.bookingService = bookingService;
        this.reservationMapper = reservationMapper;
        this.reservationLogMapper = reservationLogMapper;
        this.technicianMapper = technicianMapper;
        this.phoneCrypto = phoneCrypto;
        this.auditService = auditService;
        this.subscribeNotifier = subscribeNotifier;
    }

    public List<StaffReservationListVO> list(LocalDate from, LocalDate to, Long technicianId,
                                             Integer status, String phone, boolean owner, Long selfTechnicianId) {
        Long scopeTechnicianId = resolveTechnicianScope(technicianId, owner, selfTechnicianId);
        if (status != null && (status < Reservation.STATUS_WAIT_CONFIRM || status > Reservation.STATUS_NO_SHOW)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "status 仅支持 1-5");
        }
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start.plusDays(6) : to;
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) > MAX_QUERY_DAYS) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "查询日期范围不合法");
        }
        String phoneHash = phoneHash(phone);
        List<Reservation> reservations = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStoreId, AppConstants.STORE_ID)
                .between(Reservation::getDate, start, end)
                .eq(scopeTechnicianId != null, Reservation::getTechnicianId, scopeTechnicianId)
                .eq(status != null, Reservation::getStatus, status)
                .eq(phoneHash != null, Reservation::getContactPhoneHash, phoneHash)
                .orderByAsc(Reservation::getDate)
                .orderByAsc(Reservation::getStartTime)
                .orderByAsc(Reservation::getId));
        if (reservations.isEmpty()) {
            return List.of();
        }
        Map<Long, ServiceItem> services = data.services(
                reservations.stream().map(Reservation::getServiceId).collect(Collectors.toSet()));
        Map<Long, Technician> technicians = data.technicians(
                reservations.stream().map(Reservation::getTechnicianId).collect(Collectors.toSet()));
        return reservations.stream().map(reservation -> {
            ServiceItem service = services.get(reservation.getServiceId());
            Technician technician = technicians.get(reservation.getTechnicianId());
            return new StaffReservationListVO(
                    reservation.getId(),
                    reservation.getNo(),
                    reservation.getStatus(),
                    ReservationTexts.status(reservation.getStatus()),
                    reservation.getSource(),
                    ReservationTexts.source(reservation.getSource()),
                    reservation.getServiceId(),
                    service == null ? "" : service.getName(),
                    reservation.getTechnicianId(),
                    technician == null ? "" : technician.getName(),
                    reservation.getDate(),
                    reservation.getStartTime().format(HH_MM),
                    reservation.getEndTime().format(HH_MM),
                    service == null ? 0 : service.getPriceCents(),
                    reservation.getContactName(),
                    maskedPhone(reservation.getContactPhone()));
        }).toList();
    }

    public StaffReservationDetailVO detail(long id, boolean owner, Long selfTechnicianId) {
        return toDetail(requireVisible(id, owner, selfTechnicianId));
    }

    public StaffReservationDetailVO confirm(long id, long staffId) {
        Reservation reservation = requireExisting(id);
        if (reservation.getStatus() == Reservation.STATUS_WAIT_SERVICE) {
            return toDetail(reservation);
        }
        if (reservation.getStatus() != Reservation.STATUS_WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待确认预约可确认");
        }
        Reservation update = new Reservation();
        update.setStatus(Reservation.STATUS_WAIT_SERVICE);
        update.setConfirmedAt(LocalDateTime.now());
        int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                .eq(Reservation::getId, id)
                .eq(Reservation::getStatus, Reservation.STATUS_WAIT_CONFIRM));
        if (rows == 0) {
            Reservation latest = requireExisting(id);
            if (latest.getStatus() == Reservation.STATUS_WAIT_SERVICE) {
                return toDetail(latest);
            }
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待确认预约可确认");
        }
        writeLog(id, ReservationLog.ACTION_CONFIRM, staffId, null);
        Reservation latest = requireExisting(id);
        subscribeNotifier.reservationChanged(latest, "商家已确认", null);
        return toDetail(latest);
    }

    public StaffReservationDetailVO reject(long id, String reason, long staffId) {
        Reservation reservation = requireExisting(id);
        if (reservation.getStatus() != Reservation.STATUS_WAIT_CONFIRM) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待确认预约可拒绝");
        }
        cancel(id, Reservation.STATUS_WAIT_CONFIRM, reason, staffId, ReservationLog.ACTION_REJECT);
        return toDetail(requireExisting(id));
    }

    public StaffReservationDetailVO cancel(long id, String reason, long staffId) {
        Reservation reservation = requireExisting(id);
        if (!isActive(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可取消");
        }
        cancel(id, reservation.getStatus(), reason, staffId, ReservationLog.ACTION_CANCEL);
        return toDetail(requireExisting(id));
    }

    public StaffReservationDetailVO noShow(long id, String reason, long staffId) {
        Reservation reservation = requireExisting(id);
        if (reservation.getStatus() != Reservation.STATUS_WAIT_SERVICE) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待服务预约可标记未到店");
        }
        Reservation update = new Reservation();
        update.setStatus(Reservation.STATUS_NO_SHOW);
        int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                .eq(Reservation::getId, id)
                .eq(Reservation::getStatus, Reservation.STATUS_WAIT_SERVICE));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待服务预约可标记未到店");
        }
        writeLog(id, ReservationLog.ACTION_NO_SHOW, staffId, reason);
        return toDetail(requireExisting(id));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StaffReservationDetailVO reschedule(long id, StaffRescheduleRequest request, long staffId) {
        Reservation reservation = requireExisting(id);
        if (!isActive(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可改期");
        }
        RuleConfig rule = data.rules();
        LocalTime start = parseStartTime(request.startTime());
        int duration = (int) Duration.between(reservation.getStartTime(), reservation.getEndTime()).toMinutes();
        LocalTime end = start.plusMinutes(duration);
        List<TimeRange> businessHours = data.businessHours(request.date());
        validateOnGrid(businessHours, start, end, rule.getSlotGranularity());

        long targetTechnicianId = request.technicianId() == null || request.technicianId() <= 0
                ? reservation.getTechnicianId() : request.technicianId();
        List<Technician> allowed = data.technicians(reservation.getServiceId(), targetTechnicianId);
        Technician technician = allowed.get(0);

        technicianMapper.lockById(technician.getId());
        List<Reservation> active = reservationMapper
                .selectActiveByTechnicianAndDate(technician.getId(), request.date()).stream()
                .filter(other -> !other.getId().equals(reservation.getId()))
                .toList();
        List<TimeRange> shifts = data.shiftIntervals(List.of(technician.getId()), request.date())
                .getOrDefault(technician.getId(), List.of());
        if (!available(businessHours, shifts, active, start, end, rule.getBufferMinutes())) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "目标时段已被约满，请重新选择");
        }

        Reservation update = new Reservation();
        update.setDate(request.date());
        update.setStartTime(start);
        update.setEndTime(end);
        update.setTechnicianId(technician.getId());
        try {
            int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, id)
                    .in(Reservation::getStatus, Reservation.STATUS_WAIT_CONFIRM, Reservation.STATUS_WAIT_SERVICE));
            if (rows == 0) {
                throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可改期");
            }
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SLOT_CONFLICT, "目标时段已被约满，请重新选择");
        }
        writeLog(id, ReservationLog.ACTION_RESCHEDULE, staffId,
                slotText(reservation.getDate(), reservation.getStartTime(), reservation.getEndTime())
                        + " → " + slotText(request.date(), start, end)
                        + (java.util.Objects.equals(technician.getId(), reservation.getTechnicianId())
                                ? "" : " · 技师调整为" + technician.getName()));
        Reservation latest = requireExisting(id);
        subscribeNotifier.reservationChanged(latest, "预约已改期", request.date().format(TIME_LABEL)
                + " " + start.format(HH_MM) + "-" + end.format(HH_MM));
        return toDetail(latest);
    }

    public BookingService.SubmitResult proxy(ProxyReservationRequest request, long staffId) {
        return bookingService.submitProxy(staffId, new BookingService.ProxyBooking(
                request.serviceId(), request.technicianId(), request.date(), request.startTime(),
                request.contactName(), request.contactPhone(), request.remark(), request.idempotencyKey()));
    }

    public String fullPhone(long id, long staffId) {
        Reservation reservation = requireExisting(id);
        String phone = phoneCrypto.decrypt(reservation.getContactPhone());
        auditService.record(AuditService.OPERATOR_STAFF, staffId, "VIEW_PHONE", "reservation",
                "预约 " + reservation.getNo() + " 查看完整手机号");
        return phone;
    }

    private void cancel(long id, int fromStatus, String reason, long staffId, String action) {
        Reservation update = new Reservation();
        update.setStatus(Reservation.STATUS_CANCELLED);
        update.setCancelledAt(LocalDateTime.now());
        update.setCancelReason(reason);
        int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                .eq(Reservation::getId, id)
                .eq(Reservation::getStatus, fromStatus));
        if (rows == 0) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "当前状态不可操作");
        }
        writeLog(id, action, staffId, reason);
        Reservation latest = requireExisting(id);
        subscribeNotifier.reservationChanged(latest,
                ReservationLog.ACTION_REJECT.equals(action) ? "商家已拒绝" : "预约已取消", reason);
    }

    private Reservation requireVisible(long id, boolean owner, Long selfTechnicianId) {
        Reservation reservation = requireExisting(id);
        if (!owner && (selfTechnicianId == null || !selfTechnicianId.equals(reservation.getTechnicianId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SCOPE);
        }
        return reservation;
    }

    private Reservation requireExisting(long id) {
        Reservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "预约不存在");
        }
        return reservation;
    }

    private Long resolveTechnicianScope(Long technicianId, boolean owner, Long selfTechnicianId) {
        if (owner) {
            return technicianId != null && technicianId > 0 ? technicianId : null;
        }
        if (selfTechnicianId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ROLE, "技师账号未绑定技师档案");
        }
        if (technicianId != null && technicianId > 0 && !technicianId.equals(selfTechnicianId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SCOPE);
        }
        return selfTechnicianId;
    }

    private String phoneHash(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String normalized = PhoneCrypto.normalize(phone);
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        return phoneCrypto.hash(normalized);
    }

    private boolean isActive(int status) {
        return status == Reservation.STATUS_WAIT_CONFIRM || status == Reservation.STATUS_WAIT_SERVICE;
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
                              List<Reservation> active, LocalTime start, LocalTime end, int bufferMinutes) {
        if (!SlotCalculator.covers(businessHours, start, end) || !SlotCalculator.covers(shiftIntervals, start, end)) {
            return false;
        }
        List<SlotCalculator.BusyRange> busy = active.stream()
                .map(other -> new SlotCalculator.BusyRange(other.getStartTime(), other.getEndTime()))
                .toList();
        return !SlotCalculator.conflicts(busy, start, end, bufferMinutes);
    }

    private StaffReservationDetailVO toDetail(Reservation reservation) {
        ServiceItem service = data.service(reservation.getServiceId());
        Technician technician = data.technician(reservation.getTechnicianId());
        boolean showVerifyCode = reservation.getStatus() == Reservation.STATUS_WAIT_SERVICE;
        int duration = (int) Duration.between(reservation.getStartTime(), reservation.getEndTime()).toMinutes();
        return new StaffReservationDetailVO(
                reservation.getId(),
                reservation.getNo(),
                reservation.getStatus(),
                ReservationTexts.status(reservation.getStatus()),
                reservation.getSource(),
                ReservationTexts.source(reservation.getSource()),
                reservation.getServiceId(),
                service == null ? "" : service.getName(),
                reservation.getTechnicianId(),
                technician == null ? "" : technician.getName(),
                reservation.getDate(),
                reservation.getStartTime().format(HH_MM),
                reservation.getEndTime().format(HH_MM),
                duration,
                service == null ? 0 : service.getPriceCents(),
                reservation.getContactName(),
                maskedPhone(reservation.getContactPhone()),
                reservation.getRemark(),
                showVerifyCode ? reservation.getVerifyCode() : null,
                reservation.getCancelReason(),
                reservation.getCreatedAt(),
                reservation.getConfirmedAt(),
                reservation.getCompletedAt(),
                reservation.getCancelledAt(),
                timeline(reservation));
    }

    private List<ReservationTimelineVO> timeline(Reservation reservation) {
        return reservationLogMapper.selectList(new LambdaQueryWrapper<ReservationLog>()
                        .eq(ReservationLog::getReservationId, reservation.getId())
                        .orderByAsc(ReservationLog::getCreatedAt)
                        .orderByAsc(ReservationLog::getId)).stream()
                .map(log -> new ReservationTimelineVO(log.getAction(),
                        ReservationTexts.action(log.getAction(), reservation.getSource()),
                        log.getOperatorRole(), log.getDetail(), log.getCreatedAt()))
                .toList();
    }

    private String maskedPhone(String encrypted) {
        return encrypted == null ? null : PhoneCrypto.mask(phoneCrypto.decrypt(encrypted));
    }

    private String slotText(LocalDate date, LocalTime start, LocalTime end) {
        return date.format(TIME_LABEL) + " " + start.format(HH_MM) + "-" + end.format(HH_MM);
    }

    private void writeLog(long reservationId, String action, long staffId, String detail) {
        ReservationLog log = new ReservationLog();
        log.setReservationId(reservationId);
        log.setAction(action);
        log.setOperatorRole(ReservationLog.ROLE_OWNER);
        log.setOperatorId(staffId);
        log.setDetail(detail);
        reservationLogMapper.insert(log);
    }

    private LocalTime parseStartTime(String value) {
        if (!TIME_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "startTime 格式必须为 HH:mm");
        }
        return LocalTime.parse(value);
    }
}
