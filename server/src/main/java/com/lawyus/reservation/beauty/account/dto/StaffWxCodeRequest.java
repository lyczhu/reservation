package com.lawyus.reservation.beauty.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 商家端小程序 wx.login 的 code：绑定微信与微信快捷登录共用。
 */
public record StaffWxCodeRequest(@NotBlank String code) {
}
