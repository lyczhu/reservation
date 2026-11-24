package com.lawyus.reservation.beauty.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重置密码入参：密码至少 8 位，仅存哈希。
 */
public record StaffPasswordRequest(@NotBlank @Size(min = 8, max = 32) String password) {
}
