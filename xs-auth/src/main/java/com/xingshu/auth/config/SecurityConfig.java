package com.xingshu.auth.config;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.RSA;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.KeyPair;

/**
 * 安全配置：RSA 密钥对（JWT签名）+ BCrypt 密码加密
 * 参考 tjxt 项目的 AuthConfig 和 JwtSignerHolder
 */
@Configuration
public class SecurityConfig {

    @Bean
    public KeyPair keyPair() {
        RSA rsa = SecureUtil.rsa();
        return new KeyPair(rsa.getPublicKey(), rsa.getPrivateKey());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}