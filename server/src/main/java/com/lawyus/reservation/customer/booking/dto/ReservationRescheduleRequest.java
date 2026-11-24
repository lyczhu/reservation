package com.lawyus.reservation.customer.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 改期入参：仅调整日期与开始时间，技师与服务时长沿用原预约。
 */
public record ReservationRescheduleRequest(
        @NotNull LocalDate date,
        @NotBlank String startTime) {
}
