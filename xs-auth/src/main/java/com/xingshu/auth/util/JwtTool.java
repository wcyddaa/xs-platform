package com.xingshu.auth.util;

import cn.hutool.core.exceptions.ValidateException;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import cn.hutool.jwt.signers.JWTSigner;
import cn.hutool.jwt.signers.JWTSignerUtil;
import com.xingshu.auth.common.constants.JwtConstants;
import com.xingshu.auth.domain.dto.LoginUserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 工具类：创建/解析/校验 token
 * 参考 tjxt 项目 JwtTool 实现
 */
@Component
public class JwtTool {

    private final StringRedisTemplate stringRedisTemplate;
    private final JWTSigner jwtSigner;

    public JwtTool(StringRedisTemplate stringRedisTemplate, KeyPair keyPair) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.jwtSigner = JWTSignerUtil.createSigner("rs256", keyPair);
    }

    /**
     * 创建 access-token
     *
     * @param userDTO 用户信息
     * @return access-token
     */
    public String createToken(LoginUserDTO userDTO) {
        return JWT.create()
                .setPayload(JwtConstants.PAYLOAD_USER_KEY, userDTO)
                .setExpiresAt(new Date(System.currentTimeMillis() + JwtConstants.JWT_TOKEN_TTL.toMillis()))
                .setSigner(jwtSigner)
                .sign();
    }

    /**
     * 创建 refresh-token，并将 JTI 记录到 Redis
     *
     * @param userDTO 用户信息
     * @return refresh-token
     */
    public String createRefreshToken(LoginUserDTO userDTO) {
        // 1.生成 JTI
        String jti = UUID.randomUUID().toString(true);
        // 2.生成 token
        Duration ttl = JwtConstants.JWT_REFRESH_TTL;
        String token = JWT.create()
                .setJWTId(jti)
                .setPayload(JwtConstants.PAYLOAD_USER_KEY, userDTO)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .setSigner(jwtSigner)
                .sign();
        // 3.缓存 JTI 到 Redis，有效期与 token 一致
        stringRedisTemplate.opsForValue()
                .set(JwtConstants.JWT_REDIS_KEY_PREFIX + userDTO.getUserId(), jti, ttl);
        return token;
    }

    /**
     * 解析 access-token
     *
     * @param token access-token
     * @return 用户信息
     */
    public LoginUserDTO parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new ValidateException("token 不能为空");
        }
        // 1.校验 token
        JWT jwt = JWT.of(token).setSigner(jwtSigner);
        if (!jwt.verify()) {
            throw new ValidateException("token 无效");
        }
        // 2.校验过期
        JWTValidator.of(jwt).validateDate();
        // 3.解析用户信息
        Object userPayload = jwt.getPayload(JwtConstants.PAYLOAD_USER_KEY);
        if (userPayload == null) {
            throw new ValidateException("token 数据异常");
        }
        return ((JSONObject) userPayload).toBean(LoginUserDTO.class);
    }

    /**
     * 解析 refresh-token（含 JTI 校验）
     *
     * @param refreshToken refresh-token
     * @return 用户信息
     */
    public LoginUserDTO parseRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ValidateException("refresh-token 不能为空");
        }
        // 1.校验 token
        JWT jwt = JWT.of(refreshToken).setSigner(jwtSigner);
        if (!jwt.verify()) {
            throw new ValidateException("refresh-token 无效");
        }
        // 2.校验过期
        JWTValidator.of(jwt).validateDate();
        // 3.解析数据
        Object userPayload = jwt.getPayload(JwtConstants.PAYLOAD_USER_KEY);
        Object jtiPayload = jwt.getPayload(JwtConstants.PAYLOAD_JTI_KEY);
        if (jtiPayload == null || userPayload == null) {
            throw new ValidateException("refresh-token 数据异常");
        }
        // 4.解析用户信息
        LoginUserDTO userDTO = ((JSONObject) userPayload).toBean(LoginUserDTO.class);
        // 5.JTI 校验：Redis 中的 JTI 必须与 token 中的一致
        String cachedJti = stringRedisTemplate.opsForValue()
                .get(JwtConstants.JWT_REDIS_KEY_PREFIX + userDTO.getUserId());
        if (!jtiPayload.toString().equals(cachedJti)) {
            throw new ValidateException("refresh-token 已失效");
        }
        return userDTO;
    }

    /**
     * 清理 refresh-token 的 JTI（使 refresh-token 作废，用于登出）
     */
    public void cleanJtiCache(Long userId) {
        stringRedisTemplate.delete(JwtConstants.JWT_REDIS_KEY_PREFIX + userId);
    }
}