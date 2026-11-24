package com.lawyus.reservation.beauty.audit.dto;

/** 审计动作筛选项：动作值、中文名、业务分类与风险等级。 */
public record AuditActionVO(
        String value,
        String text,
        String category,
        String riskLevel,
        String riskText) {
}
