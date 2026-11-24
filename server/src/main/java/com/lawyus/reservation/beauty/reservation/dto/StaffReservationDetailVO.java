package com.lawyus.reservation.beauty.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.lawyus.reservation.kernel.reservation.ReservationTimelineVO;

/**
 * 商家端预约详情：核销码仅在待服务状态返回，时间线来自 reservation_log。
 */
public record StaffReservationDetailVO(
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
        int durationMin,
        int priceCents,
        String contactName,
        String contactPhone,
        String remark,
        String verifyCode,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime confirmedAt,
        LocalDateTime completedAt,
        LocalDateTime cancelledAt,
        List<ReservationTimelineVO> timeline) {
}
