package com.lawyus.reservation.customer.booking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.beauty.technician.mapper.TechnicianMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.dto.TimeRange;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.json.JsonSupport;
import com.lawyus.reservation.common.security.PhoneCrypto;
import com.lawyus.reservation.customer.booking.dto.ReservationSubmitRequest;
import com.lawyus.reservation.customer.booking.dto.ReservationSubmitVO;
import com.lawyus.reservation.integration.wx.SubscribeNotifier;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.ReservationLog;
import com.lawyus.reservation.kernel.reservation.ReservationNoGenerator;
import com.lawyus.reservation.kernel.reservation.ReservationRuleSnapshot;
import com.lawyus.reservation.kernel.reservation.ReservationTexts;
import com.lawyus.reservation.kernel.reservation.VerifyCodeGenerator;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationLogMapper;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;
import com.lawyus.reservation.kernel.slot.SlotCalculator;

/**
 * 预约提交：锁技师行串行化同一技师的并发下单，再校验排班与缓冲重叠，插入预约单与留痕。
 * 使用 READ_COMMITTED 隔离级别避免间隙锁死锁，并保证锁等待后能读到刚提交的活跃单；
 * 唯一索引（技师+日期+开始时间、幂等键）作为数据库层兜底。
 * 不指定技师时按「当日活跃单少 → id 小」的顺序依次尝试。
 */
@Service
public class BookingService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MM_DD = DateTimeFormatter.ofPattern("M月d日");
    private static final Pattern TIME_PATTERN = Pattern.compile("^(?:[01]\\d|2[0-3]):[0-5]\\d$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final BookingDataService data;
    private final ReservationMapper reservationMapper;
    private final ReservationLogMapper reservationLogMapper;
    private final TechnicianMapper technicianMapper;
    private final ReservationNoGenerator noGenerator;
    private final VerifyCodeGenerator verifyCodeGenerator;
    private final PhoneCrypto phoneCrypto;
    private final JsonSupport jsonSupport;
    private final SubscribeNotifier subscribeNotifier;

    public BookingService(BookingDataService data,
                          ReservationMapper reservationMapper,
                          ReservationLogMapper reservationLogMapper,
                          TechnicianMapper technicianMapper,
                          ReservationNoGenerator noGenerator,
                          VerifyCodeGenerator verifyCodeGenerator,
                          PhoneCrypto phoneCrypto,
                          JsonSupport jsonSupport,
                          SubscribeNotifier subscribeNotifier) {
        this.data = data;
        this.reservationMapper = reservationMapper;
        this.reservationLogMapper = reservationLogMapper;
        this.technicianMapper = technicianMapper;
        this.noGenerator = noGenerator;
        this.verifyCodeGenerator = verifyCodeGenerator;
        this.phoneCrypto = phoneCrypto;
        this.jsonSupport = jsonSupport;
        this.subscribeNotifier = subscribeNotifier;
    }

    public record SubmitResult(ReservationSubmitVO reservation, boolean idempotent) {
    }

    /**
     * 代客预约：user_id 为空，顾客身份由联系人姓名/手机号承载，来源标记为 proxy。
     */
    public record ProxyBooking(long serviceId, Long technicianId, LocalDate date, String startTime,
                               String contactName, String contactPhone, String remark, String idempotencyKey) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubmitResult submit(long userId, ReservationSubmitRequest request) {
        return create(new Draft(request.serviceId(), request.technicianId(), request.date(), request.startTime(),
                        request.contactName(), request.contactPhone(), request.remark(), request.idempotencyKey().trim()),
                userId, Reservation.SOURCE_SELF, ReservationLog.ROLE_USER, userId, true);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SubmitResult submitProxy(long staffId, ProxyBooking booking) {
        String idempotencyKey = booking.idempotencyKey() == null || booking.idempotencyKey().isBlank()
                ? UUID.randomUUID().toString() : booking.idempotencyKey().trim();
        // 店长自己录单，无需再给自己发新预约通知
        return create(new Draft(booking.serviceId(), booking.technicianId(), booking.date(), booking.startTime(),
                        booking.contactName(), booking.contactPhone(), booking.remark(), idempotencyKey),
                null, Reservation.SOURCE_PROXY, ReservationLog.ROLE_OWNER, staffId, false);
    }

    private SubmitResult create(Draft draft, Long userId, int source, int operatorRole, long operatorId,
                                boolean notify) {
        Reservation existing = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getIdempotencyKey, draft.idempotencyKey()));
        if (existing != null) {
            return new SubmitResult(toVO(existing), true);
        }

        ServiceItem service = data.requireService(draft.serviceId());
        RuleConfig rule = data.rules();
        BookingDataService.validateBookableDate(rule, draft.date());
        LocalTime start = parseStartTime(draft.startTime());
        LocalTime end = start.plusMinutes(service.getDurationMin());
        String phone = PhoneCrypto.normalize(draft.contactPhone());
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }

        List<TimeRange> businessHours = data.businessHours(draft.date());
        validateOnGrid(businessHours, start, end, rule.getSlotGranularity());
        List<Technician> technicians = data.technicians(draft.serviceId(), draft.technicianId());
        List<Reservation> active = data.activeReservations(draft.date());
        Map<Long, List<TimeRange>> shifts = data.shiftIntervals(
                technicians.stream().map(Technician::getId).toList(), draft.date());

        List<Technician> ordered = technicians.stream()
                .filter(technician -> available(businessHours, shifts.get(technician.getId()), active,
                        technician.getId(), start, end, rule, draft.date()))
                .sorted(Comparator.comparingLong((Technician technician) -> activeCount(active, technician.getId()))
                        .thenComparingLong(Technician::getId))
                .toList();

        for (Technician technician : ordered) {
            try {
                technicianMapper.lockById(technician.getId());
                List<Reservation> locked = reservationMapper.selectActiveByTechnicianAndDate(
                        technician.getId(), draft.date());
                List<TimeRange> latestShift = data.shiftIntervals(List.of(technician.getId()), draft.date())
                        .getOrDefault(technician.getId(), List.of());
                if (!available(businessHours, latestShift, locked, technician.getId(), start, end, rule, draft.date())) {
                    continue;
                }
                Reservation created = insert(draft, userId, source, service, technician, rule, start, end, phone);
                writeLog(created, operatorRole, operatorId, service, technician, start, end);
                if (notify) {
                    subscribeNotifier.reservationCreated(created);
                }
                return new SubmitResult(toVO(created), false);
            } catch (DuplicateKeyException e) {
                Reservation duplicate = reservationMapper.selectByIdempotencyKeyForUpdate(draft.idempotencyKey());
                if (duplicate != null) {
                    return new SubmitResult(toVO(duplicate), true);
                }
            }
        }
        throw new BusinessException(ErrorCode.SLOT_CONFLICT, "该时段已被约满，请重新选择");
    }

    private record Draft(long serviceId, Long technicianId, LocalDate date, String startTime,
                         String contactName, String contactPhone, String remark, String idempotencyKey) {
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
        return !SlotCalculator.conflicts(busyRanges(active, technicianId), start, end, rule.getBufferMinutes());
    }

    private List<SlotCalculator.BusyRange> busyRanges(List<Reservation> active, long technicianId) {
        return active.stream()
                .filter(reservation -> reservation.getTechnicianId() != null
                        && reservation.getTechnicianId() == technicianId)
                .map(reservation -> new SlotCalculator.BusyRange(
                        reservation.getStartTime(), reservation.getEndTime()))
                .toList();
    }

    private long activeCount(List<Reservation> active, long technicianId) {
        return active.stream()
                .filter(reservation -> reservation.getTechnicianId() != null
                        && reservation.getTechnicianId() == technicianId)
                .count();
    }

    private Reservation insert(Draft draft, Long userId, int source, ServiceItem service,
                               Technician technician, RuleConfig rule, LocalTime start, LocalTime end,
                               String phone) {
        for (int attempt = 0; attempt < 5; attempt++) {
            Reservation reservation = new Reservation();
            reservation.setNo(noGenerator.next());
            reservation.setStoreId(AppConstants.STORE_ID);
            reservation.setUserId(userId);
            reservation.setServiceId(service.getId());
            reservation.setTechnicianId(technician.getId());
            reservation.setDate(draft.date());
            reservation.setStartTime(start);
            reservation.setEndTime(end);
            reservation.setStatus(rule.getNeedConfirm() == 1
                    ? Reservation.STATUS_WAIT_CONFIRM : Reservation.STATUS_WAIT_SERVICE);
            reservation.setContactName(draft.contactName().trim());
            reservation.setContactPhone(phoneCrypto.encrypt(phone));
            reservation.setContactPhoneHash(phoneCrypto.hash(phone));
            reservation.setRemark(draft.remark() == null || draft.remark().isBlank()
                    ? null : draft.remark().trim());
            reservation.setSource(source);
            reservation.setVerifyCode(uniqueVerifyCode(reservation.getNo()));
            reservation.setIdempotencyKey(draft.idempotencyKey());
            reservation.setRuleSnapshot(jsonSupport.write(snapshot(rule)));
            reservation.setVersion(0);
            try {
                reservationMapper.insert(reservation);
                return reservation;
            } catch (DuplicateKeyException e) {
                if (!isDuplicateOf(e, "uk_no") && !isDuplicateOf(e, "uk_verify_active")) {
                    throw e;
                }
            }
        }
        throw new BusinessException(ErrorCode.SLOT_CONFLICT, "预约号生成冲突，请重试");
    }

    private String uniqueVerifyCode(String no) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = verifyCodeGenerator.generate(no, attempt);
            if (reservationMapper.countActiveByVerifyCode(code) == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "核销码生成冲突");
    }

    private ReservationRuleSnapshot snapshot(RuleConfig rule) {
        return new ReservationRuleSnapshot(rule.getSlotGranularity(), rule.getBookableDays(), rule.getMinLeadMinutes(),
                rule.getCancelDeadlineHours(), rule.getRescheduleDeadlineHours(), rule.getNeedConfirm(),
                rule.getMaxConcurrent(), rule.getBufferMinutes(), rule.getReminderHours());
    }

    private void writeLog(Reservation reservation, int operatorRole, long operatorId, ServiceItem service,
                          Technician technician, LocalTime start, LocalTime end) {
        ReservationLog log = new ReservationLog();
        log.setReservationId(reservation.getId());
        log.setAction(ReservationLog.ACTION_SUBMIT);
        log.setOperatorRole(operatorRole);
        log.setOperatorId(operatorId);
        log.setDetail(service.getName() + " · " + technician.getName()
                + " · " + reservation.getDate().format(MM_DD)
                + " " + start.format(HH_MM) + "-" + end.format(HH_MM));
        reservationLogMapper.insert(log);
    }

    private ReservationSubmitVO toVO(Reservation reservation) {
        ServiceItem service = data.service(reservation.getServiceId());
        Technician technician = data.technician(reservation.getTechnicianId());
        return new ReservationSubmitVO(
                reservation.getId(),
                reservation.getNo(),
                reservation.getVerifyCode(),
                reservation.getId() + ":" + reservation.getVerifyCode(),
                reservation.getStatus(),
                statusText(reservation.getStatus()),
                reservation.getServiceId(),
                service == null ? "" : service.getName(),
                reservation.getTechnicianId(),
                technician == null ? "" : technician.getName(),
                reservation.getDate(),
                reservation.getStartTime().format(HH_MM),
                reservation.getEndTime().format(HH_MM),
                service == null ? 0 : service.getPriceCents());
    }

    private String statusText(int status) {
        return ReservationTexts.status(status);
    }

    private boolean isDuplicateOf(DuplicateKeyException e, String keyName) {
        String message = e.getMessage();
        return message != null && message.contains(keyName);
    }

    private LocalTime parseStartTime(String value) {
        if (!TIME_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "startTime 格式必须为 HH:mm");
        }
        return LocalTime.parse(value);
    }
}
