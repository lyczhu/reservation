package com.lawyus.reservation.beauty.reservation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 代客预约入参：idempotencyKey 可空，服务端自动生成；来源固定为 proxy。
 */
public record ProxyReservationRequest(
        @NotNull Long serviceId,
        Long technicianId,
        @NotNull LocalDate date,
        @NotBlank String startTime,
        @NotBlank @Size(max = 20) String contactName,
        @NotBlank @Size(max = 20) String contactPhone,
        @Size(max = 100) String remark,
        @Size(max = 36) String idempotencyKey) {
}
