package com.lawyus.reservation.beauty.account;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.beauty.account.dto.StaffLoginRequest;
import com.lawyus.reservation.beauty.account.dto.StaffLoginResponse;
import com.lawyus.reservation.beauty.account.dto.StaffWxCodeRequest;
import com.lawyus.reservation.beauty.account.mapper.StaffAccountMapper;
import com.lawyus.reservation.common.api.AppConstants;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.config.AppProperties;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.StpStaffUtil;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import me.chanjar.weixin.common.error.WxErrorException;

/**
 * 商家端登录：BCrypt 校验密码，停用账号拒绝登录；
 * 微信快捷登录按 staff_account.wx_openid 匹配，未配置小程序 AppID 时仅在 dev 降级。
 * 用户名或密码错误统一返回同一提示，避免账号枚举。
 */
@Service
public class StaffAuthService {

    private final StaffAccountMapper staffAccountMapper;
    private final WxMaService wxStaffMaService;
    private final AppProperties properties;
    private final Environment environment;
    private final BCryptPasswordEncoder passwordEncoder;

    public StaffAuthService(StaffAccountMapper staffAccountMapper,
                            @Qualifier("wxStaffMaService") WxMaService wxStaffMaService,
                            AppProperties properties,
                            Environment environment,
                            BCryptPasswordEncoder passwordEncoder) {
        this.staffAccountMapper = staffAccountMapper;
        this.wxStaffMaService = wxStaffMaService;
        this.properties = properties;
        this.environment = environment;
        this.passwordEncoder = passwordEncoder;
    }

    public StaffLoginResponse login(StaffLoginRequest request) {
        StaffAccount account = staffAccountMapper.selectOne(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getStoreId, AppConstants.STORE_ID)
                .eq(StaffAccount::getUsername, request.username()));
        if (account == null || !passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "用户名或密码错误");
        }
        if (account.getStatus() != StaffAccount.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return doLogin(account);
    }

    public StaffLoginResponse wxLogin(StaffWxCodeRequest request) {
        String openid = resolveOpenid(request.code());
        StaffAccount account = staffAccountMapper.selectOne(new LambdaQueryWrapper<StaffAccount>()
                .eq(StaffAccount::getStoreId, AppConstants.STORE_ID)
                .eq(StaffAccount::getWxOpenid, openid));
        if (account == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "该微信未绑定商家账号");
        }
        if (account.getStatus() != StaffAccount.STATUS_ENABLED) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        return doLogin(account);
    }

    /**
     * code2session 换取 openid；未配置商家端小程序 AppID 时仅在 dev 降级为 code 直通。
     */
    public String resolveOpenid(String code) {
        if (properties.getWx().getStaff().getAppId().isBlank()) {
            requireDev("商家端微信登录");
            return "dev_" + code;
        }
        try {
            WxMaJscode2SessionResult session = wxStaffMaService.getUserService().getSessionInfo(code);
            return session.getOpenid();
        } catch (WxErrorException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "微信登录失败：" + e.getMessage());
        }
    }

    public void logout() {
        StpStaffUtil.logout();
    }

    private StaffLoginResponse doLogin(StaffAccount account) {
        String role = account.getRole() == StaffAccount.ROLE_OWNER ? "owner" : "technician";
        StpStaffUtil.login(account.getId(), role, account.getTechnicianId());

        StaffAccount update = new StaffAccount();
        update.setId(account.getId());
        update.setLastLoginAt(LocalDateTime.now());
        staffAccountMapper.updateById(update);

        return new StaffLoginResponse(StpStaffUtil.getTokenValue(), role, account.getId(), account.getTechnicianId());
    }

    private void requireDev(String action) {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, action + "未配置小程序 AppID");
        }
    }
}
