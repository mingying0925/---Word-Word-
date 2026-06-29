package com.skillbridge.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 对称加密工具。
 * <p>
 * 用于敏感数据（如身份证号）的加密存储。
 * <p>
 * 特性：
 * <ul>
 *   <li>AES-256-GCM：提供机密性 + 完整性认证。</li>
 *   <li>每次加密生成随机 IV（12 字节），密文格式：Base64(IV + ciphertext + tag)。</li>
 *   <li>密钥从环境变量注入，SHA-256 派生为 256 位。</li>
 * </ul>
 */
@Component
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;      // GCM 推荐 12 字节 IV
    private static final int TAG_LENGTH = 128;     // 认证标签长度（位）
    private static final int KEY_LENGTH = 32;      // 256 位密钥

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoUtil(@Value("${app.crypto.secret:${JWT_SECRET:SkillBridgeLocalDevSecretKey2026}}") String secret) {
        // 通过 SHA-256 派生固定长度密钥
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, 0, KEY_LENGTH, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }

    /**
     * 加密明文。
     *
     * @param plaintext 明文字符串
     * @return Base64 编码的密文（含 IV）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + 密文
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param ciphertext Base64 编码的密文（含 IV）
     * @return 明文字符串
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(encrypted);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败", e);
        }
    }

    /**
     * 脱敏显示：保留前 6 位和后 4 位，中间用 * 代替。
     * 例如：440301199001011234 → 440301********1234
     */
    public static String mask(String plaintext) {
        if (plaintext == null || plaintext.length() < 10) {
            return plaintext;
        }
        return plaintext.substring(0, 6) + "********" + plaintext.substring(plaintext.length() - 4);
    }
}
