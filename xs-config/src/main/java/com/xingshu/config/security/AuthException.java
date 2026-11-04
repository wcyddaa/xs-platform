package com.xingshu.config.security;

/**
 * 鉴权异常：status 会作为 HTTP 状态码返回（401 未登录 / 403 无权限 / 503 依赖服务不可用）。
 */
public class AuthException extends RuntimeException {

    private final int status;

    public AuthException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public static AuthException unauthorized(String message) {
        return new AuthException(401, message);
    }

    public static AuthException forbidden(String message) {
        return new AuthException(403, message);
    }

    public static AuthException unavailable(String message) {
        return new AuthException(503, message);
    }
}
