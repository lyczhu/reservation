package com.lawyus.reservation.beauty.category.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CategoryStatusRequest(@NotNull @Min(1) @Max(2) Integer status) {
}
