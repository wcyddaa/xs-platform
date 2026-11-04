package com.xingshu.auth.common.constants;

import java.time.Duration;

/**
 * JWT 相关常量
 */
public class JwtConstants {

    /** JWT payload 中的用户信息 key */
    public static final String PAYLOAD_USER_KEY = "user";

    /** JWT payload 中的 JTI key */
    public static final String PAYLOAD_JTI_KEY = "jti";

    /** Redis 中 refresh-token JTI 的 key 前缀 */
    public static final String JWT_REDIS_KEY_PREFIX = "jwt:uid:";

    /** access-token 有效期：30分钟 */
    public static final Duration JWT_TOKEN_TTL = Duration.ofMinutes(30);

    /** refresh-token 有效期：7天 */
    public static final Duration JWT_REFRESH_TTL = Duration.ofDays(7);

    /** 请求头中的 token 名称 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    /** 请求头中的 refresh-token 名称 */
    public static final String REFRESH_HEADER = "X-Refresh-Token";

    /** 请求头中的用户信息 key（由拦截器注入） */
    public static final String USER_HEADER = "X-User-Info";

    /** 权限缓存 Hash key */
    public static final String AUTH_PRIVILEGE_KEY = "auth:privileges";

    /** 权限缓存版本号 key */
    public static final String AUTH_PRIVILEGE_VERSION_KEY = "auth:privileges:version";
}