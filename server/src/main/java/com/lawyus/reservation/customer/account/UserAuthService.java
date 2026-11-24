package com.lawyus.reservation.customer.account;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawyus.reservation.common.api.ErrorCode;
import com.lawyus.reservation.common.config.AppProperties;
import com.lawyus.reservation.common.exception.BusinessException;
import com.lawyus.reservation.common.security.PhoneCrypto;
import com.lawyus.reservation.common.security.StpUserUtil;
import com.lawyus.reservation.customer.account.dto.UserLoginRequest;
import com.lawyus.reservation.customer.account.dto.UserLoginResponse;
import com.lawyus.reservation.customer.account.dto.UserProfileVO;
import com.lawyus.reservation.customer.account.mapper.UserMapper;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * 顾客登录与手机号绑定：正式环境走微信 code2session / getPhoneNumber；
 * 未配置小程序 AppID 时仅在 dev 降级（code 直接作为 openid 后缀或手机号），其它环境拒绝。
 */
@Service
public class UserAuthService {

    private final UserMapper userMapper;
    private final WxMaService wxUserMaService;
    private final AppProperties properties;
    private final Environment environment;
    private final PhoneCrypto phoneCrypto;

    public UserAuthService(UserMapper userMapper,
                           @Qualifier("wxUserMaService") WxMaService wxUserMaService,
                           AppProperties properties,
                           Environment environment,
                           PhoneCrypto phoneCrypto) {
        this.userMapper = userMapper;
        this.wxUserMaService = wxUserMaService;
        this.properties = properties;
        this.environment = environment;
        this.phoneCrypto = phoneCrypto;
    }

    public UserLoginResponse login(UserLoginRequest request) {
        String openid = resolveOpenid(request.code());
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setNickname("微信用户");
            userMapper.insert(user);
        }
        StpUserUtil.login(user.getId());
        return new UserLoginResponse(StpUserUtil.getTokenValue(), user.getId(), user.getNickname(),
                user.getAvatar(), user.getPhone() != null);
    }

    public String bindPhone(long userId, String code) {
        String phone = PhoneCrypto.normalize(resolvePhone(code));
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        User update = new User();
        update.setId(userId);
        update.setPhone(phoneCrypto.encrypt(phone));
        update.setPhoneHash(phoneCrypto.hash(phone));
        userMapper.updateById(update);
        return PhoneCrypto.mask(phone);
    }

    public UserProfileVO profile(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        String phone = user.getPhone() == null ? null : PhoneCrypto.mask(phoneCrypto.decrypt(user.getPhone()));
        return new UserProfileVO(user.getId(), user.getNickname(), user.getAvatar(), phone);
    }

    private String resolveOpenid(String code) {
        if (properties.getWx().getUser().getAppId().isBlank()) {
            requireDev("微信登录");
            return "dev_" + code;
        }
        try {
            WxMaJscode2SessionResult session = wxUserMaService.getUserService().getSessionInfo(code);
            return session.getOpenid();
        } catch (WxErrorException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "微信登录失败：" + e.getMessage());
        }
    }

    private String resolvePhone(String code) {
        if (properties.getWx().getUser().getAppId().isBlank()) {
            requireDev("手机号获取");
            return code;
        }
        try {
            WxMaPhoneNumberInfo info = wxUserMaService.getUserService().getPhoneNumber(code);
            return info.getPurePhoneNumber();
        } catch (WxErrorException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "手机号获取失败：" + e.getMessage());
        }
    }

    private void requireDev(String action) {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, action + "未配置小程序 AppID");
        }
    }
}
