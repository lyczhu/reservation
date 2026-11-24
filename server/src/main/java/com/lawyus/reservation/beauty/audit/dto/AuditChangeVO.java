package com.lawyus.reservation.beauty.audit.dto;

/** 审计详情中的单个字段变更，供前端展示「变更前 → 变更后」。 */
public record AuditChangeVO(
        String field,
        String label,
        String before,
        String after) {
}
