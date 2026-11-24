package com.lawyus.reservation.customer.booking.dto;

import java.time.LocalDate;

/**
 * 我的预约列表项：canCancel/canReschedule 由规则快照时限实时计算，供前端决定操作入口。
 */
public record ReservationListVO(
        long id,
        String no,
        int status,
        String statusText,
        String serviceName,
        String serviceCover,
        String technicianName,
        LocalDate date,
        String startTime,
        String endTime,
        int priceCents,
        boolean canCancel,
        boolean canReschedule) {
}
