package com.lawyus.reservation.beauty.audit.dto;

/** 当前筛选条件下的审计概览，用于页面顶部统计卡。 */
public record AuditSummaryVO(
        long total,
        long highRisk,
        long today,
        long last7Days,
        int operatorCount) {
}
