package com.lawyus.reservation.beauty.technician.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 复制排班入参：sourceWeekStart 必须为周一（被复制的自然周起始日），weeks 为向后复制的周数（1-8）。
 */
public record ShiftCopyRequest(@NotNull LocalDate sourceWeekStart,
                               @NotNull @Min(1) @Max(8) Integer weeks) {
}
