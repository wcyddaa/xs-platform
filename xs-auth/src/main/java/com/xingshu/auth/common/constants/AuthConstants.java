package com.xingshu.auth.common.constants;

import java.util.List;
import java.util.Map;

/**
 * 权限常量：角色 → 路由绑定
 */
public class AuthConstants {

    /** 角色 ID */
    public static final int ROLE_ADMIN = 1;
    public static final int ROLE_TEACHER = 2;
    public static final int ROLE_STUDENT = 3;

    /** 无需认证的路径 */
    public static final List<String> EXCLUDE_PATHS = List.of(
            "POST:/auth/login",
            "POST:/auth/refresh",
            "GET:/auth/public/**"
    );

    /**
     * 角色 → 允许访问的路由（Ant 风格路径）
     * key: 角色ID, value: 允许访问的路径列表
     */
    public static final Map<Integer, List<String>> ROLE_ROUTES = Map.of(
            ROLE_ADMIN, List.of(
                    "/users/**",
                    "/classes/**",
                    "/courses/**",
                    "/textbooks/**",
                    "/auth/**",
                    "/admin/**"
            ),
            ROLE_TEACHER, List.of(
                    "/chapters/**",
                    "/homework/**",
                    "/auth/**",
                    "/teacher/**"
            ),
            ROLE_STUDENT, List.of(
                    "/homework/my/**",
                    "/homework/submit/**",
                    "/chat/**",
                    "/auth/**",
                    "/student/**"
            )
    );
}