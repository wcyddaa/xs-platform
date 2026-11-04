package com.xingshu.auth.service;

import com.xingshu.auth.domain.dto.LoginFormDTO;
import com.xingshu.auth.domain.dto.LoginUserDTO;
import com.xingshu.auth.domain.dto.TokenResponse;

/**
 * 认证服务接口
 */
public interface IAuthService {

    /**
     * 用户登录：验证用户名密码 → 生成 token
     */
    TokenResponse login(LoginFormDTO loginForm);

    /**
     * 刷新 token：用 refresh-token 换新的 access-token
     */
    TokenResponse refreshToken(String refreshToken);

    /**
     * 用户登出：使 refresh-token 作废
     */
    void logout(Long userId);
}