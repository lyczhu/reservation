package com.lawyus.reservation.kernel.reservation;

/**
 * 预约创建时的规则快照，存于 reservation.rule_snapshot；
 * 顾客取消/改期时限只读此快照，不受后续规则调整影响。
 */
public record ReservationRuleSnapshot(
        int slotGranularity,
        int bookableDays,
        int minLeadMinutes,
        int cancelDeadlineHours,
        int rescheduleDeadlineHours,
        int needConfirm,
        int maxConcurrent,
        int bufferMinutes,
        int reminderHours) {
}
