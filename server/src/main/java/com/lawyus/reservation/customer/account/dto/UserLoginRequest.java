package com.lawyus.reservation.customer.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 微信登录入参：code 为 wx.login 返回的临时凭证，dev 环境可直接传任意标识。
 */
public record UserLoginRequest(@NotBlank String code) {
}
