package com.xingshu.config.security;

/**
 * 请求级用户上下文（ThreadLocal），由 AuthInterceptor 写入、请求结束后清理。
 */
public final class AuthContext {

    private static final ThreadLocal<AuthUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(AuthUser user) {
        HOLDER.set(user);
    }

    public static AuthUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static AuthUser requireLogin() {
        AuthUser user = HOLDER.get();
        if (user == null) {
            throw AuthException.unauthorized("请先登录");
        }
        return user;
    }

    public static AuthUser requireAdmin() {
        AuthUser user = requireLogin();
        if (!user.isAdmin()) {
            throw AuthException.forbidden("仅管理员可执行该操作");
        }
        return user;
    }

    public static AuthUser requireAdminOrTeacher() {
        AuthUser user = requireLogin();
        if (!user.isAdmin() && !user.isTeacher()) {
            throw AuthException.forbidden("仅管理员或教师可访问");
        }
        return user;
    }

    public static AuthUser requireInternal() {
        AuthUser user = requireLogin();
        if (!user.internal()) {
            throw AuthException.forbidden("仅内部服务可访问");
        }
        return user;
    }
}
