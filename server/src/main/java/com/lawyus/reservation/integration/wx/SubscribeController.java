package com.lawyus.reservation.integration.wx;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.api.Result;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.StpStaffUtil;
import com.lawyus.reservation.common.security.StpUserUtil;
import com.lawyus.reservation.integration.wx.dto.SubscribeAuthRequest;
import com.lawyus.reservation.kernel.subscribe.SubscribeQuota;

import jakarta.validation.Valid;

/**
 * 订阅授权上报：小程序 requestSubscribeMessage 成功后调用，按登录身份累计顾客或商家配额。
 */
@RestController
@RequestMapping("/api/v1/subscribe")
public class SubscribeController {

    private final WxSubscribeService wxSubscribeService;

    public SubscribeController(WxSubscribeService wxSubscribeService) {
        this.wxSubscribeService = wxSubscribeService;
    }

    @PostMapping("/auth")
    public Result<Void> auth(@Valid @RequestBody SubscribeAuthRequest request) {
        if (StpUserUtil.stpLogic.isLogin()) {
            wxSubscribeService.authorize(SubscribeQuota.USER_TYPE_USER, StpUserUtil.getUserId(), request.templates());
            return Result.ok();
        }
        if (StpStaffUtil.stpLogic.isLogin()) {
            wxSubscribeService.authorize(SubscribeQuota.USER_TYPE_STAFF, StpStaffUtil.getStaffId(), request.templates());
            return Result.ok();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
