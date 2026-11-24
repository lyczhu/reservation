package com.lawyus.reservation.customer.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 提交预约入参：technicianId 为空或 0 表示不指定技师；
 * idempotencyKey 由客户端生成并复用，用于防止网络重试产生重复单。
 */
public record ReservationSubmitRequest(
        @NotNull Long serviceId,
        Long technicianId,
        @NotNull LocalDate date,
        @NotBlank String startTime,
        @NotBlank @Size(max = 20) String contactName,
        @NotBlank @Size(max = 20) String contactPhone,
        @Size(max = 100) String remark,
        @NotBlank @Size(max = 36) String idempotencyKey,
        List<String> subscribeTemplates) {
}
