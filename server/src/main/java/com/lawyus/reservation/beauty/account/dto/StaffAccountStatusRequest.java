package com.lawyus.reservation.beauty.account.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 账号启停入参：1 启用 / 2 停用。
 */
public record StaffAccountStatusRequest(@NotNull @Min(1) @Max(2) Integer status) {
}
