package com.lawyus.reservation.beauty.reservation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商家写操作原因：拒绝/取消/未到店均必填。
 */
public record StaffReasonRequest(@NotBlank @Size(max = 50) String reason) {
}
