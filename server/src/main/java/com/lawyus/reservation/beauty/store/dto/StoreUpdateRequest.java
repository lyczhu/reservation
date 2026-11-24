package com.lawyus.reservation.beauty.store.dto;

import com.lawyus.reservation.common.dto.TimeRange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record StoreUpdateRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 500) String logo,
        @Size(max = 3) List<@Size(max = 500) String> banner,
        @Size(max = 200) String address,
        BigDecimal lat,
        BigDecimal lng,
        @Size(max = 20) String phone,
        @NotNull Map<String, List<TimeRange>> businessHours,
        @Size(max = 60) String announcement) {
}
