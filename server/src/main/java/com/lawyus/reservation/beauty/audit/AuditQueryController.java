package com.lawyus.reservation.beauty.audit;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.beauty.audit.dto.AuditActionVO;
import com.lawyus.reservation.beauty.audit.dto.AuditQueryResult;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.OwnerGuard;

/**
 * 审计日志查询接口（仅店长）：按业务分类/动作/操作人/日期/关键词筛选，
 * 返回带中文摘要、风险等级和字段变更的倒序分页结果。
 */
@RestController
@RequestMapping("/api/v1/b/audits")
public class AuditQueryController {

    private final AuditQueryService auditQueryService;

    public AuditQueryController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/actions")
    public Result<List<AuditActionVO>> actions() {
        OwnerGuard.check();
        return Result.ok(auditQueryService.actions());
    }

    @GetMapping
    public Result<AuditQueryResult> query(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer operatorType,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        OwnerGuard.check();
        return Result.ok(auditQueryService.query(action, category, operatorType, operatorId, from, to, keyword, page, size));
    }
}
