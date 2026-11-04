package com.xingshu.auth.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenResponse {
    /** access-token */
    private String accessToken;

    /** refresh-token */
    private String refreshToken;

    /** token 有效期（秒） */
    private long expiresIn;

    /** 用户信息 */
    private LoginUserDTO userInfo;
}