package com.xingshu.common.context;

/**
 * 当前登录用户上下文（ThreadLocal）
 * 由 TokenUserFilter 在请求进入时设置，请求结束后自动清理
 */
public class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    public static void clear() {
        USER_ID_HOLDER.remove();
    }
}