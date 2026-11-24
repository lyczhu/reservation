package com.lawyus.reservation.beauty.technician.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ShiftUpdateRequest(@NotEmpty List<@Valid ShiftEntryRequest> entries) {
}
