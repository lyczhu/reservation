package com.lawyus.reservation.beauty.dashboard.dto;

/**
 * 看板预约色块：conflict 标识活跃单已不被技师当前排班覆盖（排班变更后需店长处理），前端标红。
 */
public record DashboardBlockVO(
        long id,
        String no,
        long technicianId,
        String serviceName,
        String startTime,
        String endTime,
        int status,
        String statusText,
        boolean conflict) {
}
