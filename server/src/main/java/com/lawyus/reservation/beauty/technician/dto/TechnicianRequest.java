package com.lawyus.reservation.beauty.technician.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 技师新建/编辑入参；accountUsername/accountPassword 用于同步创建技师账号（可选，仅在该技师尚无账号时生效）。
 */
public record TechnicianRequest(
        @NotBlank @Size(max = 20) String name,
        @NotBlank @Size(max = 500) String avatar,
        @Size(max = 20) String title,
        @Size(max = 200) String intro,
        @Size(max = 20) String accountUsername,
        @Size(min = 6, max = 32) String accountPassword) {
}
