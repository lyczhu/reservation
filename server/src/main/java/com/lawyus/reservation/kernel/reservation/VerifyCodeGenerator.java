package com.lawyus.reservation.kernel.reservation;

import com.lawyus.reservation.common.config.AppProperties;
import com.lawyus.reservation.common.security.KeyMaterial;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * 核销码生成：HMAC-SHA256(种子 + 尝试序号) 取数值后 6 位补零；
 * 活跃单内撞码时由调用方递增尝试序号重算。
 */
@Component
public class VerifyCodeGenerator {

    private final SecretKeySpec key;

    public VerifyCodeGenerator(AppProperties properties) {
        this.key = new SecretKeySpec(KeyMaterial.resolve(properties.getSecurity().getVerifyCodeSecret(),
                "app-dev-verify-code", "app.security.verify-code-secret"), "HmacSHA256");
    }

    public String generate(String seed, int attempt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal((seed + ":" + attempt).getBytes(StandardCharsets.UTF_8));
            int value = ((digest[0] & 0x7f) << 24) | ((digest[1] & 0xff) << 16)
                    | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
            return String.format("%06d", value % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("核销码生成失败", e);
        }
    }
}
