package com.lawyus.reservation.customer.account;

import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.security.StpUserUtil;
import com.lawyus.reservation.customer.account.dto.PhoneBindRequest;
import com.lawyus.reservation.customer.account.dto.UserLoginRequest;
import com.lawyus.reservation.customer.account.dto.UserLoginResponse;
import com.lawyus.reservation.customer.account.dto.UserProfileVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顾客端登录、手机号绑定与个人资料接口。
 */
@RestController
@RequestMapping("/api/v1")
public class UserAuthController {

    private final UserAuthService userAuthService;

    public UserAuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/auth/user/login")
    public Result<UserLoginResponse> login(@Valid @RequestBody UserLoginRequest request) {
        return Result.ok(userAuthService.login(request));
    }

    @PostMapping("/auth/user/phone")
    public Result<String> bindPhone(@Valid @RequestBody PhoneBindRequest request) {
        StpUserUtil.checkLogin();
        return Result.ok(userAuthService.bindPhone(StpUserUtil.getUserId(), request.code()));
    }

    @GetMapping("/c/user/profile")
    public Result<UserProfileVO> profile() {
        StpUserUtil.checkLogin();
        return Result.ok(userAuthService.profile(StpUserUtil.getUserId()));
    }
}
