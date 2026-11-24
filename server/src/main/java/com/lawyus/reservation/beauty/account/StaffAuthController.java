package com.lawyus.reservation.beauty.account;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.beauty.account.dto.StaffLoginRequest;
import com.lawyus.reservation.beauty.account.dto.StaffLoginResponse;
import com.lawyus.reservation.beauty.account.dto.StaffWxCodeRequest;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.StpStaffUtil;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.validation.Valid;

/**
 * 商家端登录与退出接口。
 */
@RestController
@RequestMapping("/api/v1/auth/staff")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/login")
    public Result<StaffLoginResponse> login(@Valid @RequestBody StaffLoginRequest request) {
        return Result.ok(staffAuthService.login(request));
    }

    @PostMapping("/wx-login")
    public Result<StaffLoginResponse> wxLogin(@Valid @RequestBody StaffWxCodeRequest request) {
        return Result.ok(staffAuthService.wxLogin(request));
    }

    @SaCheckLogin(type = StpStaffUtil.TYPE)
    @PostMapping("/logout")
    public Result<Void> logout() {
        staffAuthService.logout();
        return Result.ok();
    }
}
