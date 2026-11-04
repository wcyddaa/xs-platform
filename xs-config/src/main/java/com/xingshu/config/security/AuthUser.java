package com.xingshu.config.security;

/**
 * 当前请求的登录用户（由 xs-auth 校验 token 后解析）。
 * internal 为 true 表示内部服务调用（携带内部令牌），视为管理员权限。
 */
public record AuthUser(Long userId, String username, String name, Integer roleId, boolean internal) {

    public boolean isAdmin() {
        return internal || Integer.valueOf(1).equals(roleId);
    }

    public boolean isTeacher() {
        return !internal && Integer.valueOf(2).equals(roleId);
    }

    public boolean isStudent() {
        return !internal && Integer.valueOf(3).equals(roleId);
    }
}
