package com.lawyus.reservation.beauty.audit.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志列表项：操作人名称按类型从商家账号或顾客昵称补齐，
 * 同时返回可直接展示的分类、风险、摘要、字段变更与友好对象名。
 */
public record AuditLogVO(
        long id,
        int operatorType,
        String operatorTypeText,
        long operatorId,
        String operatorName,
        String action,
        String actionText,
        String target,
        String targetText,
        String detail,
        String ip,
        String ipText,
        LocalDateTime createdAt,
        String category,
        String riskLevel,
        String riskText,
        String summary,
        List<AuditChangeVO> changes) {
}
