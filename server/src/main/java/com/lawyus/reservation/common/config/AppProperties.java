package com.lawyus.reservation.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * 项目自定义配置（前缀 app）：安全密钥、RustFS 对象存储、微信小程序（user/staff 双实例）。
 */
@Component
@ConfigurationProperties(prefix = "app")
@Getter
public class AppProperties {

    private final Security security = new Security();
    private final Rustfs rustfs = new Rustfs();
    private final Wx wx = new Wx();

    @Getter
    @Setter
    public static class Security {

        private String phoneAesKey = "";
        private String phoneHashKey = "";
        private String verifyCodeSecret = "";
    }

    @Getter
    @Setter
    public static class Rustfs {

        private String endpoint = "http://localhost:9000";
        private String accessKey = "";
        private String secretKey = "";
        private String bucket = "reservation";
        private String region = "us-east-1";
    }

    @Getter
    public static class Wx {

        private final App user = new App();
        private final App staff = new App();
        private final Subscribe subscribe = new Subscribe();
    }

    /**
     * 一次性订阅模板 ID（微信公众平台申请）；未配置时发送降级为日志。
     */
    @Getter
    @Setter
    public static class Subscribe {

        private String successTemplateId = "";
        private String changeTemplateId = "";
        private String reminderTemplateId = "";
        private String newReservationTemplateId = "";
    }

    @Getter
    @Setter
    public static class App {

        private String appId = "";
        private String appSecret = "";
    }

}