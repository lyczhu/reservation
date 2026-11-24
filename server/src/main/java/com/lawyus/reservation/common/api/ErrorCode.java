package com.lawyus.reservation.common.api;

import lombok.Getter;

/**
 * 统一业务错误码，前端按 code 区分处理（0 为成功）。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAM_INVALID(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录或登录已失效"),
    ACCOUNT_DISABLED(40102, "账号已停用"),
    FORBIDDEN_ROLE(40301, "角色无权限"),
    FORBIDDEN_SCOPE(40302, "数据范围越权"),
    NOT_FOUND(40401, "资源不存在"),
    SLOT_CONFLICT(40901, "时段冲突"),
    IDEMPOTENT_HIT(40902, "重复提交（幂等命中）"),
    ILLEGAL_STATE(40903, "状态流转非法"),
    DEADLINE_EXCEEDED(40904, "超出取消/改期时限"),
    CONTENT_UNSAFE(42201, "内容安全违规"),
    VERIFY_CODE_WRONG(42202, "核销码错误"),
    VERIFY_LOCKED(42203, "核销已锁定"),
    RESOURCE_UNAVAILABLE(42204, "服务或技师不可约"),
    QUOTA_EMPTY(42205, "订阅配额不足"),
    INTERNAL_ERROR(50001, "服务内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
