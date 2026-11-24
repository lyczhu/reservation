package com.lawyus.reservation.beauty.dashboard.dto;

/**
 * 看板统计：预计营收只计待服务与已完成单。
 */
public record DashboardStatsVO(
        int total,
        int waitConfirm,
        int waitService,
        int completed,
        int revenueCents) {
}
