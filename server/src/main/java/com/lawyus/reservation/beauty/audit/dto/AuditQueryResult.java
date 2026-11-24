package com.lawyus.reservation.beauty.audit.dto;

import java.util.List;

/** 审计分页结果与概览统计的组合响应。 */
public record AuditQueryResult(
        List<AuditLogVO> records,
        long total,
        long current,
        long size,
        AuditSummaryVO summary) {
}
