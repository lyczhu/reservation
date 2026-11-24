package com.lawyus.reservation.common.security;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 密钥解析：配置值支持 Base64 或原始文本；未配置时使用开发默认值并告警，
 * 生产环境必须通过环境变量注入。
 */
@Slf4j
public final class KeyMaterial {

    private KeyMaterial() {
    }

    public static byte[] resolve(String configured, String devSeed, String name) {
        if (configured == null || configured.isBlank()) {
            log.warn("{} 未配置，使用开发默认密钥（生产环境必须通过环境变量注入）", name);
            return sha256(devSeed.getBytes(StandardCharsets.UTF_8));
        }
        String value = configured.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length >= 16) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // 非 Base64 时按原始文本处理
        }
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        if (raw.length < 16) {
            throw new IllegalStateException(name + " 密钥至少需要 16 字节");
        }
        return raw;
    }

    public static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
