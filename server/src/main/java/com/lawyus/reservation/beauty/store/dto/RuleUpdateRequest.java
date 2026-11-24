package com.lawyus.reservation.beauty.store.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RuleUpdateRequest(
        @NotNull @Min(30) @Max(60) Integer slotGranularity,
        @NotNull @Min(7) @Max(30) Integer bookableDays,
        @NotNull @Min(0) @Max(1440) Integer minLeadMinutes,
        @NotNull @Min(0) @Max(24) Integer cancelDeadlineHours,
        @NotNull @Min(0) @Max(24) Integer rescheduleDeadlineHours,
        @NotNull @Min(0) @Max(1) Integer needConfirm,
        @NotNull @Min(0) @Max(30) Integer bufferMinutes,
        @NotNull @Min(1) @Max(24) Integer reminderHours) {
}
