package com.xingshu.auth.interceptor;

import com.xingshu.auth.common.constants.AuthErrorInfo;
import com.xingshu.auth.common.exception.AuthException;
import com.xingshu.auth.domain.dto.LoginUserDTO;
import com.xingshu.auth.util.JwtTool;
import com.xingshu.auth.util.PrivilegeCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import static com.xingshu.auth.common.constants.JwtConstants.AUTHORIZATION_HEADER;

/**
 * 认证拦截器：校验 token → 提取用户信息 → 校验权限
 * 参考 tjxt 项目 AuthUtil（Gateway 版本）和 LoginAuthInterceptor
 *
 * 注意：异常由 GlobalExceptionHandler 统一处理，拦截器中直接抛出 AuthException
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTool jwtTool;
    private final PrivilegeCache privilegeCache;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // 1.白名单路径直接放行
        if (privilegeCache.isExcludePath(method, uri)) {
            return true;
        }

        // 2.获取 token
        String token = request.getHeader(AUTHORIZATION_HEADER);
        if (token == null || token.isBlank()) {
            throw AuthException.unauthorized(AuthErrorInfo.Msg.NOT_LOGIN);
        }

        // 3.解析 token，获取用户信息
        LoginUserDTO user;
        try {
            user = jwtTool.parseToken(token);
        } catch (Exception e) {
            log.warn("token 解析失败: {}", e.getMessage());
            throw AuthException.unauthorized(AuthErrorInfo.Msg.INVALID_TOKEN);
        }

        // 4.校验角色权限
        if (!privilegeCache.hasPermission(user.getRoleId(), uri)) {
            log.warn("用户 {}（角色: {}）无权限访问 {}", user.getUsername(), user.getRoleId(), uri);
            throw AuthException.forbidden(AuthErrorInfo.Msg.FORBIDDEN);
        }

        // 5.存入 ThreadLocal，供后续 Controller 使用
        LoginUserContext.setUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止内存泄漏
        LoginUserContext.clear();
    }
}