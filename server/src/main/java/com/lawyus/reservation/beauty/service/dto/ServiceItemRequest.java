package com.lawyus.reservation.beauty.service.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 服务新建/编辑入参：technicianIds 为可约技师，保存时整体替换 service_technician 关联。
 */
public record ServiceItemRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 30) String name,
        @NotBlank @Size(max = 500) String cover,
        @Size(max = 9) List<@Size(max = 500) String> images,
        @NotNull @Min(15) @Max(240) Integer durationMin,
        @NotNull @Min(0) Integer priceCents,
        @Size(max = 500) String description,
        @Size(max = 200) String notes,
        Integer sort,
        @NotEmpty List<Long> technicianIds) {
}
