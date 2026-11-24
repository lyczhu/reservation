package com.lawyus.reservation.beauty.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.beauty.dashboard.dto.DashboardVO;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.StpStaffUtil;

/**
 * 今日看板接口（商家端，店长与技师均可访问，技师为受限视图）。
 */
@RestController
@RequestMapping("/api/v1/b/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public Result<DashboardVO> get(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(dashboardService.get(date, StpStaffUtil.isOwner(), StpStaffUtil.getTechnicianId()));
    }
}
