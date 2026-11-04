package com.xingshu.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * xs-config 鉴权拦截器：
 * 1. 内部服务携带 X-Internal-Token 时放行，并以内部身份执行；
 * 2. 其余 /api/** 请求必须携带有效 Authorization，由 xs-auth 校验并解析角色。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final AuthTokenVerifier tokenVerifier;
    private final String internalToken;

    public AuthInterceptor(AuthTokenVerifier tokenVerifier,
                           @Value("${xingshu.internal-token:xingshu-internal-2026}") String internalToken) {
        this.tokenVerifier = tokenVerifier;
        this.internalToken = internalToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String requestInternalToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (requestInternalToken != null && !requestInternalToken.isBlank()
                && internalToken != null && internalToken.equals(requestInternalToken)) {
            // 内部令牌含模型密钥读取权限，仅允许本机服务调用，避免局域网内被滥用
            if (!isLoopback(request.getRemoteAddr())) {
                throw AuthException.forbidden("内部接口仅允许本机访问");
            }
            AuthContext.set(new AuthUser(null, "internal", "内部服务", 1, true));
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7).trim();
        }
        if (token == null || token.isBlank()) {
            throw AuthException.unauthorized("请先登录");
        }
        AuthContext.set(tokenVerifier.verify(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        AuthContext.clear();
    }

    private static boolean isLoopback(String address) {
        if (address == null) {
            return false;
        }
        return "127.0.0.1".equals(address)
                || "::1".equals(address)
                || "0:0:0:0:0:0:0:1".equals(address)
                || address.startsWith("127.");
    }
}
