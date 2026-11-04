package com.xingshu.auth.common.constants;

/**
 * 认证错误码常量
 * 参考 tjxt 项目 AuthErrorInfo
 */
public class AuthErrorInfo {

    /** 错误码 */
    public static class Code {
        /** 无效 token */
        public static final int INVALID_TOKEN_CODE = 401;
        /** token 过期 */
        public static final int EXPIRED_TOKEN_CODE = 401;
        /** 无权限 */
        public static final int FORBIDDEN_CODE = 403;
        /** 登录失败 */
        public static final int LOGIN_FAILED_CODE = 400;
    }

    /** 错误消息 */
    public static class Msg {
        public static final String INVALID_TOKEN = "无效的token";
        public static final String INVALID_TOKEN_PAYLOAD = "token数据异常";
        public static final String EXPIRED_TOKEN = "token已过期";
        public static final String FORBIDDEN = "无权限访问该资源";
        public static final String INVALID_REFRESH_TOKEN = "refresh-token无效或已失效";
        public static final String NOT_LOGIN = "未登录，请先登录";
        public static final String LOGIN_FAILED = "用户名或密码错误，或账号已被禁用";
        public static final String PARAM_INVALID = "请求参数不合法";
    }
}