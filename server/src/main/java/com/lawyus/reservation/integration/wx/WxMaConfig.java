package com.lawyus.reservation.integration.wx;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.lawyus.reservation.common.config.AppProperties;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;

/**
 * 微信小程序客户端：user/staff 为两个独立小程序（不同 AppID）；
 * AppID 未配置时仍创建实例，内容安全检测由 ContentSecurityService 自行跳过。
 */
@Configuration
public class WxMaConfig {

    @Bean
    public WxMaService wxUserMaService(AppProperties properties) {
        return build(properties.getWx().getUser());
    }

    @Bean
    public WxMaService wxStaffMaService(AppProperties properties) {
        return build(properties.getWx().getStaff());
    }

    private WxMaService build(AppProperties.App app) {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(app.getAppId());
        config.setSecret(app.getAppSecret());
        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}
