package com.lawyus.reservation.beauty.account.dto;

import java.time.LocalDateTime;

/**
 * 账号列表项：微信绑定仅返回是否已绑定，openid 不外泄。
 */
public record StaffAccountVO(
        long id,
        String username,
        int role,
        String roleText,
        Long technicianId,
        String technicianName,
        boolean wxBound,
        int status,
        String statusText,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt) {
}
