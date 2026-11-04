package com.xingshu.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感配置加解密（AES-256-GCM）。
 * <p>
 * 主密钥来源优先级：
 * <ol>
 *   <li>环境变量 XINGSHU_SECRET_KEY（推荐，生产环境由密钥管理系统注入）</li>
 *   <li>本地文件 %USERPROFILE%/XingShuEnv/secret.key（首次自动生成）</li>
 * </ol>
 * 主密钥与密文都不进入代码仓库，数据库里只保存密文。
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher() {
        this.keySpec = new SecretKeySpec(loadKey(), "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank() || plainText.startsWith(PREFIX)) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("敏感配置加密失败", e);
        }
    }

    /** 兼容历史明文数据：非 enc:v1: 前缀直接返回 */
    public String decrypt(String stored) {
        if (stored == null || stored.isBlank() || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感配置解密失败，请检查 XINGSHU_SECRET_KEY 是否与写入时一致", e);
        }
    }

    private byte[] loadKey() {
        try {
            String envKey = System.getenv("XINGSHU_SECRET_KEY");
            if (envKey != null && !envKey.isBlank()) {
                log.info("使用环境变量 XINGSHU_SECRET_KEY 作为配置加密主密钥");
                return deriveKey(envKey);
            }
            Path keyFile = Paths.get(System.getProperty("user.home"), "XingShuEnv", "secret.key");
            if (Files.exists(keyFile)) {
                String content = Files.readString(keyFile, StandardCharsets.UTF_8).trim();
                log.info("使用本地密钥文件作为配置加密主密钥: {}", keyFile);
                return deriveKey(content);
            }
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            String encoded = Base64.getEncoder().encodeToString(generated);
            Files.createDirectories(keyFile.getParent());
            Files.writeString(keyFile, encoded, StandardCharsets.UTF_8);
            log.info("已生成本地配置加密主密钥: {}（请勿提交到代码仓库）", keyFile);
            return generated;
        } catch (IOException e) {
            throw new IllegalStateException("读取配置加密主密钥失败", e);
        }
    }

    /** 任意长度口令统一派生为 256 位密钥 */
    private byte[] deriveKey(String raw) {
        try {
            if (raw.length() >= 32 && !raw.contains(" ")) {
                byte[] direct = Base64.getDecoder().decode(raw);
                if (direct.length == 32 || direct.length == 16) {
                    return MessageDigest.getInstance("SHA-256").digest(direct);
                }
            }
        } catch (Exception ignored) {
            // 非 Base64 或摘要算法不可用，走派生
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("派生配置加密密钥失败", e);
        }
    }
}
