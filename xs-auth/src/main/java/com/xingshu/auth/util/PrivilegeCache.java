package com.xingshu.auth.util;

import com.xingshu.auth.common.constants.AuthConstants;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 权限缓存工具：从 Redis 加载路由权限，内存缓存 + 定时刷新
 * 参考 tjxt 项目 PrivilegeCache 实现
 */
@Slf4j
@Component
public class PrivilegeCache {

    private final StringRedisTemplate stringRedisTemplate;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    public PrivilegeCache(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 启动时初始化角色路由到 Redis
     */
    @PostConstruct
    public void initRouteCache() {
        log.info("初始化角色路由缓存...");
        AuthConstants.ROLE_ROUTES.forEach((roleId, routes) -> {
            String key = "auth:routes:" + roleId;
            stringRedisTemplate.delete(key);
            stringRedisTemplate.opsForList().rightPushAll(key, routes);
            log.info("角色 {} 绑定路由: {}", roleId, routes);
        });
        log.info("角色路由缓存初始化完成");
    }

    /**
     * 校验用户是否有权限访问指定路径
     *
     * @param roleId     用户角色 ID
     * @param requestUri 请求路径
     * @return true-有权限, false-无权限
     */
    public boolean hasPermission(Integer roleId, String requestUri) {
        List<String> routes = AuthConstants.ROLE_ROUTES.get(roleId);
        if (routes == null) {
            return false;
        }
        return routes.stream().anyMatch(pattern -> antPathMatcher.match(pattern, requestUri));
    }

    /**
     * 判断路径是否在白名单中（无需认证）
     */
    public boolean isExcludePath(String method, String requestUri) {
        String antPath = method + ":" + requestUri;
        return AuthConstants.EXCLUDE_PATHS.stream()
                .anyMatch(pattern -> antPathMatcher.match(pattern, antPath));
    }
}