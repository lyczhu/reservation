package com.lawyus.reservation.beauty.dashboard.dto;

import java.time.LocalDate;
import java.util.List;

import com.lawyus.reservation.common.dto.TimeRange;

/**
 * 今日看板：按营业时段生成时间轴，技师为纵轴，预约以色块状态展示。
 */
public record DashboardVO(
        LocalDate date,
        List<TimeRange> businessHours,
        List<String> slotStarts,
        List<DashboardTechnicianVO> technicians,
        List<DashboardBlockVO> reservations,
        DashboardStatsVO stats) {
}
