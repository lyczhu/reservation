package com.lawyus.reservation.beauty.reservation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.reservation.dto.VerifyRequest;
import com.lawyus.reservation.beauty.reservation.dto.VerifyResultVO;
import com.lawyus.reservation.beauty.service.ServiceItem;
import com.lawyus.reservation.beauty.technician.Technician;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.audit.AuditService;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.customer.booking.BookingDataService;
import com.lawyus.reservation.integration.wx.SubscribeNotifier;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.ReservationLog;
import com.lawyus.reservation.kernel.reservation.ReservationTexts;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationLogMapper;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;

/**
 * 核销：二维码（预约ID:核销码）或 6 位数字码匹配待服务单并完成。
 * 连错 3 次按操作账号锁定 5 分钟（Redis 计数），成功即清零；锁定事件写审计日志。
 * 重复核销已完成的单按幂等成功返回。
 */
@Slf4j
@Service
public class StaffVerifyService {

    private static final int MAX_FAILS = 3;
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern CODE_PATTERN = Pattern.compile("^\\d{6}$");
    private static final Pattern QR_PATTERN = Pattern.compile("^(\\d+):(\\d{6})$");

    private final ReservationMapper reservationMapper;
    private final ReservationLogMapper reservationLogMapper;
    private final BookingDataService data;
    private final StringRedisTemplate redisTemplate;
    private final AuditService auditService;
    private final SubscribeNotifier subscribeNotifier;

    public StaffVerifyService(ReservationMapper reservationMapper,
                              ReservationLogMapper reservationLogMapper,
                              BookingDataService data,
                              StringRedisTemplate redisTemplate,
                              AuditService auditService,
                              SubscribeNotifier subscribeNotifier) {
        this.reservationMapper = reservationMapper;
        this.reservationLogMapper = reservationLogMapper;
        this.data = data;
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
        this.subscribeNotifier = subscribeNotifier;
    }

    public VerifyResultVO verify(long staffId, boolean owner, Long selfTechnicianId, VerifyRequest request) {
        ensureNotLocked(staffId);
        Reservation reservation = resolve(staffId, owner, selfTechnicianId, request);
        if (reservation.getStatus() == Reservation.STATUS_COMPLETED) {
            clearFailures(staffId);
            return toVO(reservation, true);
        }
        if (reservation.getStatus() != Reservation.STATUS_WAIT_SERVICE) {
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待服务预约可核销");
        }

        Reservation update = new Reservation();
        update.setStatus(Reservation.STATUS_COMPLETED);
        update.setCompletedAt(LocalDateTime.now());
        int rows = reservationMapper.update(update, new LambdaUpdateWrapper<Reservation>()
                .eq(Reservation::getId, reservation.getId())
                .eq(Reservation::getStatus, Reservation.STATUS_WAIT_SERVICE));
        if (rows == 0) {
            Reservation latest = reservationMapper.selectById(reservation.getId());
            if (latest != null && latest.getStatus() == Reservation.STATUS_COMPLETED) {
                clearFailures(staffId);
                return toVO(latest, true);
            }
            throw new BusinessException(ErrorCode.ILLEGAL_STATE, "仅待服务预约可核销");
        }
        writeLog(reservation.getId(), staffId, owner);
        clearFailures(staffId);
        Reservation completed = reservationMapper.selectById(reservation.getId());
        subscribeNotifier.reservationChanged(completed, "已完成服务", null);
        return toVO(completed, false);
    }

    private Reservation resolve(long staffId, boolean owner, Long selfTechnicianId, VerifyRequest request) {
        String qrContent = request == null ? null : trimToNull(request.qrContent());
        String code = request == null ? null : trimToNull(request.code());
        if (qrContent != null) {
            Matcher matcher = QR_PATTERN.matcher(qrContent);
            if (!matcher.matches()) {
                throw wrongCode(staffId);
            }
            Reservation reservation = reservationMapper.selectById(Long.parseLong(matcher.group(1)));
            if (reservation == null || !matcher.group(2).equals(reservation.getVerifyCode())) {
                throw wrongCode(staffId);
            }
            ensureScope(reservation, owner, selfTechnicianId);
            return reservation;
        }
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw wrongCode(staffId);
        }
        List<Reservation> matches = reservationMapper.selectConfirmableByVerifyCode(code);
        if (!owner) {
            matches = matches.stream()
                    .filter(reservation -> selfTechnicianId != null
                            && selfTechnicianId.equals(reservation.getTechnicianId()))
                    .toList();
        }
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            Reservation completed = reservationMapper.selectLatestCompletedByVerifyCode(code);
            if (completed != null && (owner || (selfTechnicianId != null
                    && selfTechnicianId.equals(completed.getTechnicianId())))) {
                return completed;
            }
        }
        throw wrongCode(staffId);
    }

    private void ensureScope(Reservation reservation, boolean owner, Long selfTechnicianId) {
        if (!owner && (selfTechnicianId == null || !selfTechnicianId.equals(reservation.getTechnicianId()))) {
            throw new BusinessException(ErrorCode.FORBIDDEN_SCOPE);
        }
    }

    private BusinessException wrongCode(long staffId) {
        int fails = recordFailure(staffId);
        if (fails >= MAX_FAILS) {
            auditService.record(AuditService.OPERATOR_STAFF, staffId, "VERIFY_LOCK", "staff_account",
                    "核销码连续输错 " + fails + " 次，锁定 5 分钟");
            return new BusinessException(ErrorCode.VERIFY_LOCKED, "核销已锁定，请 5 分钟后重试");
        }
        return new BusinessException(ErrorCode.VERIFY_CODE_WRONG);
    }

    private void ensureNotLocked(long staffId) {
        if (readFailures(staffId) < MAX_FAILS) {
            return;
        }
        throw new BusinessException(ErrorCode.VERIFY_LOCKED,
                "核销已锁定，请 " + remainingSeconds(staffId) + " 秒后重试");
    }

    private void writeLog(long reservationId, long staffId, boolean owner) {
        ReservationLog log = new ReservationLog();
        log.setReservationId(reservationId);
        log.setAction(ReservationLog.ACTION_VERIFY);
        log.setOperatorRole(owner ? ReservationLog.ROLE_OWNER : ReservationLog.ROLE_TECHNICIAN);
        log.setOperatorId(staffId);
        reservationLogMapper.insert(log);
    }

    private VerifyResultVO toVO(Reservation reservation, boolean alreadyCompleted) {
        ServiceItem service = data.service(reservation.getServiceId());
        Technician technician = data.technician(reservation.getTechnicianId());
        return new VerifyResultVO(
                reservation.getId(),
                reservation.getNo(),
                reservation.getStatus(),
                ReservationTexts.status(reservation.getStatus()),
                service == null ? "" : service.getName(),
                technician == null ? "" : technician.getName(),
                reservation.getDate(),
                reservation.getStartTime().format(HH_MM),
                reservation.getEndTime().format(HH_MM),
                reservation.getContactName(),
                alreadyCompleted);
    }

    private String key(long staffId) {
        return "verify:fail:" + staffId;
    }

    private int readFailures(long staffId) {
        try {
            String value = redisTemplate.opsForValue().get(key(staffId));
            return value == null ? 0 : Integer.parseInt(value);
        } catch (RuntimeException e) {
            log.warn("核销失败计数读取失败: {}", e.getMessage());
            return 0;
        }
    }

    private int recordFailure(long staffId) {
        try {
            Long count = redisTemplate.opsForValue().increment(key(staffId));
            redisTemplate.expire(key(staffId), LOCK_TTL);
            return count == null ? 1 : count.intValue();
        } catch (RuntimeException e) {
            log.warn("核销失败计数写入失败: {}", e.getMessage());
            return 0;
        }
    }

    private long remainingSeconds(long staffId) {
        try {
            Long ttl = redisTemplate.getExpire(key(staffId), TimeUnit.SECONDS);
            return ttl == null || ttl <= 0 ? LOCK_TTL.toSeconds() : ttl;
        } catch (RuntimeException e) {
            return LOCK_TTL.toSeconds();
        }
    }

    private void clearFailures(long staffId) {
        try {
            redisTemplate.delete(key(staffId));
        } catch (RuntimeException e) {
            log.warn("核销失败计数清理失败: {}", e.getMessage());
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
