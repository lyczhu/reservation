package com.lawyus.reservation.beauty.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新建技师账号：登录名用手机号格式，必须绑定一名不超过一个账号的技师档案。
 */
public record StaffAccountCreateRequest(
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "登录名必须为手机号格式") String username,
        @NotBlank @Size(min = 8, max = 32) String password,
        @NotNull Long technicianId) {
}
