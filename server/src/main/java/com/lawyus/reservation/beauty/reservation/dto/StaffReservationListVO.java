package com.lawyus.reservation.beauty.reservation.dto;

import java.time.LocalDate;

/**
 * 商家端预约列表项：手机号脱敏，来源区分自助/代客。
 */
public record StaffReservationListVO(
        long id,
        String no,
        int status,
        String statusText,
        int source,
        String sourceText,
        long serviceId,
        String serviceName,
        long technicianId,
        String technicianName,
        LocalDate date,
        String startTime,
        String endTime,
        int priceCents,
        String contactName,
        String contactPhone) {
}
