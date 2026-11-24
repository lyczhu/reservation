package com.lawyus.reservation.customer.booking.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.lawyus.reservation.kernel.reservation.ReservationTimelineVO;

/**
 * 预约详情：核销码仅在待服务状态返回；timeline 来自 reservation_log。
 */
public record ReservationDetailVO(
        long id,
        String no,
        int status,
        String statusText,
        long serviceId,
        String serviceName,
        String serviceCover,
        long technicianId,
        String technicianName,
        String technicianAvatar,
        LocalDate date,
        String startTime,
        String endTime,
        int durationMin,
        int priceCents,
        String contactName,
        String contactPhone,
        String remark,
        String verifyCode,
        String qrContent,
        String cancelReason,
        LocalDateTime createdAt,
        boolean canCancel,
        boolean canReschedule,
        List<ReservationTimelineVO> timeline) {
}
