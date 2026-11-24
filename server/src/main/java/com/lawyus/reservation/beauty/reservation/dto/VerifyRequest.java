package com.lawyus.reservation.beauty.reservation.dto;

/**
 * 核销入参：扫码传 qrContent（预约ID:核销码），手输传 6 位 code。
 */
public record VerifyRequest(String code, String qrContent) {
}
