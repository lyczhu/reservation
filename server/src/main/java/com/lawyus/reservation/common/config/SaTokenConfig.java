package com.lawyus.reservation.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.lawyus.reservation.common.security.StpStaffUtil;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;

/**
 * Sa-Token 鉴权：/api/v1/b/** 统一要求 staff 登录；
 * C 端接口默认匿名，需登录的接口在方法上标注 @SaCheckLogin(type = "user")。
 * 多账号 StpLogic 以静态常量持有，不注册为 Bean（SaBeanInject 要求容器内唯一 StpLogic）。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle ->
                        SaRouter.match("/api/v1/b/**")
                                .notMatch(SaHttpMethod.OPTIONS)
                                .check(r -> StpStaffUtil.checkLogin())))
                .addPathPatterns("/api/v1/**");
    }
}
