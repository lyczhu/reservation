package com.lawyus.reservation.common.security;

import com.lawyus.reservation.common.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 手机号加密与检索：AES-256-GCM 随机 IV 存储密文，HMAC-SHA256 生成等值查询哈希。
 * 密钥来自 app.security（PHONE_AES_KEY / PHONE_HASH_KEY）。
 */
@Component
public class PhoneCrypto {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;

    public PhoneCrypto(AppProperties properties) {
        byte[] aes = KeyMaterial.resolve(properties.getSecurity().getPhoneAesKey(),
                "app-dev-phone-aes", "app.security.phone-aes-key");
        this.aesKey = new SecretKeySpec(aes.length == 32 ? aes : KeyMaterial.sha256(aes), "AES");
        this.hmacKey = new SecretKeySpec(KeyMaterial.resolve(properties.getSecurity().getPhoneHashKey(),
                "app-dev-phone-hash", "app.security.phone-hash-key"), "HmacSHA256");
    }

    public String encrypt(String phone) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(normalize(phone).getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("手机号加密失败", e);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] payload = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, payload, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("手机号解密失败", e);
        }
    }

    public String hash(String phone) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(normalize(phone).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("手机号哈希失败", e);
        }
    }

    public static String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }

    public static String mask(String phone) {
        String digits = normalize(phone);
        if (digits.length() != 11) {
            return digits;
        }
        return digits.substring(0, 3) + "****" + digits.substring(7);
    }
}
