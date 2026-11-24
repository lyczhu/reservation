package com.lawyus.reservation.beauty.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ServiceStatusRequest(@NotNull @Min(1) @Max(2) Integer status) {
}
