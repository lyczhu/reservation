package com.lawyus.reservation.common.security;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 顾客端登录态（Sa-Token 多账号体系：loginType = user）。
 */
public final class StpUserUtil {

    public static final String TYPE = "user";
    public static final StpLogic stpLogic = new StpLogic(TYPE);

    private StpUserUtil() {
    }

    public static void login(Object id) {
        stpLogic.login(id);
    }

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    public static long getUserId() {
        return stpLogic.getLoginIdAsLong();
    }

    public static void logout() {
        stpLogic.logout();
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }
}