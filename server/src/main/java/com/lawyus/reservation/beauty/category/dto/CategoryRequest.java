package com.lawyus.reservation.beauty.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 10) String name,
        @NotBlank @Size(max = 500) String icon,
        Integer sort) {
}
