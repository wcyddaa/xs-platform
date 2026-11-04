package com.xingshu.auth.interceptor;

import com.xingshu.auth.domain.dto.LoginUserDTO;

/**
 * ThreadLocal 用户上下文：存储当前请求的登录用户信息
 * 参考 tjxt 项目 UserContext
 */
public class LoginUserContext {

    private static final ThreadLocal<LoginUserDTO> USER_HOLDER = new ThreadLocal<>();

    public static void setUser(LoginUserDTO user) {
        USER_HOLDER.set(user);
    }

    public static LoginUserDTO getUser() {
        return USER_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}