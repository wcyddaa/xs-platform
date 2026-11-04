package com.xingshu.auth.common.exception;

import lombok.Getter;

/**
 * 认证异常
 */
@Getter
public class AuthException extends RuntimeException {

    private final int code;

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AuthException(String message) {
        this(400, message);
    }

    /** 未登录 */
    public static AuthException unauthorized(String message) {
        return new AuthException(401, message);
    }

    /** 无权限 */
    public static AuthException forbidden(String message) {
        return new AuthException(403, message);
    }

    /** 请求参数错误 */
    public static AuthException badRequest(String message) {
        return new AuthException(400, message);
    }
}