package com.lawyus.reservation.customer.account.dto;

public record UserLoginResponse(String token, long userId, String nickname, String avatar, boolean phoneBound) {
}
