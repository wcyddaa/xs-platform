package com.xingshu.common.filter;

import cn.hutool.json.JSONObject;
import cn.hutool.jwt.JWT;
import com.xingshu.common.context.UserContextHolder;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Token 用户过滤器：从 Authorization 头中解析 JWT，提取 userId 存入 UserContextHolder
 * 供 MyMetaObjectHandler 自动填充 createrId/updaterId 使用
 */
@Component
public class TokenUserFilter implements Filter {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Autowired(required = false)
    private ApiCallCounter apiCallCounter;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String token = httpRequest.getHeader(AUTHORIZATION_HEADER);
                if (token != null && !token.isBlank()) {
                    try {
                        JWT jwt = JWT.of(token);
                        Object userPayload = jwt.getPayload("user");
                        if (userPayload instanceof JSONObject userJson) {
                            Long userId = userJson.getLong("userId");
                            if (userId != null) {
                                UserContextHolder.setUserId(userId);
                            }
                        }
                    } catch (Exception ignored) {
                        // token 解析失败，忽略（放行未认证请求）
                    }
                }
            }
            // 每日 API 调用计数（Redis 可用时）
            if (apiCallCounter != null) {
                apiCallCounter.increment();
            }
            chain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}