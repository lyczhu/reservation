package com.lawyus.reservation.customer.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 手机号绑定入参：正式环境 code 来自 getPhoneNumber，dev 环境直接传 11 位手机号。
 */
public record PhoneBindRequest(@NotBlank String code) {
}
