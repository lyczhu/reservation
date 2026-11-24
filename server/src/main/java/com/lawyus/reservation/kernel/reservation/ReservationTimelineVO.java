package com.lawyus.reservation.kernel.reservation;

import java.time.LocalDateTime;

/**
 * 预约操作时间线项，由 reservation_log 生成，顾客端与商家端共用。
 */
public record ReservationTimelineVO(
        String action,
        String actionText,
        int operatorRole,
        String detail,
        LocalDateTime createdAt) {
}
