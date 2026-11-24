package com.lawyus.reservation.common.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

public record SortRequest(@NotEmpty List<Long> ids) {
}
