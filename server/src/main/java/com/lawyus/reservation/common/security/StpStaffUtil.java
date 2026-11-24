package com.lawyus.reservation.common.security;

import cn.dev33.satoken.stp.StpLogic;

/**
 * 商家端登录态（Sa-Token 多账号体系：loginType = staff）。
 * 会话内携带角色（owner/technician）与 technicianId，用于受限视图。
 */
public final class StpStaffUtil {

    public static final String TYPE = "staff";
    public static final StpLogic stpLogic = new StpLogic(TYPE);

    private StpStaffUtil() {
    }

    public static void login(long staffId, String role, Long technicianId) {
        stpLogic.login(staffId);
        stpLogic.getSession().set("role", role);
        if (technicianId != null) {
            stpLogic.getSession().set("technicianId", technicianId);
        }
    }

    public static void checkLogin() {
        stpLogic.checkLogin();
    }

    public static long getStaffId() {
        return stpLogic.getLoginIdAsLong();
    }

    public static String getRole() {
        return stpLogic.getSession().getString("role");
    }

    public static Long getTechnicianId() {
        Object value = stpLogic.getSession().get("technicianId");
        return value == null ? null : ((Number) value).longValue();
    }

    public static boolean isOwner() {
        return "owner".equals(getRole());
    }

    public static String getTokenValue() {
        return stpLogic.getTokenValue();
    }

    public static void logout() {
        stpLogic.logout();
    }

    /** 踢下线：账号停用等场景立即失效该账号全部会话。 */
    public static void kickout(long staffId) {
        stpLogic.kickout(staffId);
    }
}