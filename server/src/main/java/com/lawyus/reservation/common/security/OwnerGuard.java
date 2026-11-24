package com.lawyus.reservation.common.security;

import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.exception.BusinessException;

/**
 * 店长权限断言：/api/v1/b/** 已由 Sa-Token 拦截器保证登录，此处再限定 owner 角色（技师为受限视图）。
 */
public final class OwnerGuard {

    private OwnerGuard() {
    }

    public static void check() {
        if (!StpStaffUtil.isOwner()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ROLE);
        }
    }
}