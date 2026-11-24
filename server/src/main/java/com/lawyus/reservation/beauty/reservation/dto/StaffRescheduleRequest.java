package com.lawyus.reservation.beauty.reservation.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 商家改期：可不传 technicianId（沿用原技师），商家操作不受顾客时限约束。
 */
public record StaffRescheduleRequest(
        @NotNull LocalDate date,
        @NotBlank String startTime,
        Long technicianId) {
}
