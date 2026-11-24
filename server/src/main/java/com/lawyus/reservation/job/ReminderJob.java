package com.lawyus.reservation.job;

import java.time.LocalDateTime;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawyus.reservation.beauty.store.RuleConfig;
import com.lawyus.reservation.beauty.store.mapper.RuleConfigMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.integration.wx.SubscribeNotifier;
import com.lawyus.reservation.kernel.reservation.Reservation;
import com.lawyus.reservation.kernel.reservation.mapper.ReservationMapper;

/**
 * 到店提醒：每分钟扫描待服务且未提醒的预约，开始时间落在 reminder_hours 窗口内即发送并置 reminded_at。
 * 先以 reminded_at IS NULL 条件置位成功再发送，多实例并发下同一预约只提醒一次。
 */
@Slf4j
@Component
public class ReminderJob {

    private final RuleConfigMapper ruleConfigMapper;
    private final ReservationMapper reservationMapper;
    private final SubscribeNotifier subscribeNotifier;

    public ReminderJob(RuleConfigMapper ruleConfigMapper,
                       ReservationMapper reservationMapper,
                       SubscribeNotifier subscribeNotifier) {
        this.ruleConfigMapper = ruleConfigMapper;
        this.reservationMapper = reservationMapper;
        this.subscribeNotifier = subscribeNotifier;
    }

    @Scheduled(cron = "0 * * * * ?")
    public void remindDueReservations() {
        RuleConfig rule = ruleConfigMapper.selectById(AppConstants.STORE_ID);
        if (rule == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime limit = now.plusHours(rule.getReminderHours());
        List<Reservation> candidates = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, Reservation.STATUS_WAIT_SERVICE)
                .isNull(Reservation::getRemindedAt)
                .ge(Reservation::getDate, now.toLocalDate())
                .le(Reservation::getDate, limit.toLocalDate()));
        for (Reservation reservation : candidates) {
            LocalDateTime startAt = LocalDateTime.of(reservation.getDate(), reservation.getStartTime());
            if (startAt.isBefore(now) || startAt.isAfter(limit)) {
                continue;
            }
            int rows = reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .set(Reservation::getRemindedAt, now)
                    .eq(Reservation::getId, reservation.getId())
                    .isNull(Reservation::getRemindedAt));
            if (rows == 1) {
                subscribeNotifier.reservationReminder(reservation);
                log.info("[提醒] 已触发到店提醒 reservation={} start={}", reservation.getId(), startAt);
            }
        }
    }
}
