package com.lawyus.reservation.customer.booking.dto;

import java.time.LocalDate;

/**
 * 提交结果：核销码用于数字输入，qrContent（预约ID:核销码）用于生成二维码。
 */
public record ReservationSubmitVO(
        long id,
        String no,
        String verifyCode,
        String qrContent,
        int status,
        String statusText,
        long serviceId,
        String serviceName,
        long technicianId,
        String technicianName,
        LocalDate date,
        String startTime,
        String endTime,
        int priceCents) {
}
