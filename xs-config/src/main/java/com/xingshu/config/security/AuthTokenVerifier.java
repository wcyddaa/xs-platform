package com.xingshu.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 调用 xs-auth /auth/me 校验访问令牌，返回登录用户（含角色）。
 * 结果短时缓存，避免同一页面多个请求重复校验。
 */
@Component
public class AuthTokenVerifier {

    private static final long CACHE_TTL_MILLIS = 60_000L;
    private static final int CACHE_MAX_SIZE = 2000;

    private final RestTemplate restTemplate;
    private final String authBaseUrl;

    private final Map<String, CachedUser> cache = new ConcurrentHashMap<>();

    public AuthTokenVerifier(RestTemplate restTemplate,
                             @Value("${xingshu.auth.base-url:http://localhost:8081}") String authBaseUrl) {
        this.restTemplate = restTemplate;
        this.authBaseUrl = authBaseUrl;
    }

    public AuthUser verify(String token) {
        long now = System.currentTimeMillis();
        CachedUser cached = cache.get(token);
        if (cached != null && cached.expiresAt() > now) {
            return cached.user();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", token);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    authBaseUrl + "/auth/me", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null || !Integer.valueOf(200).equals(asInt(body.get("code")))) {
                throw AuthException.unauthorized("登录状态无效或已过期");
            }
            Map<?, ?> data = (Map<?, ?>) body.get("data");
            if (data == null || data.get("userId") == null) {
                throw AuthException.unauthorized("登录状态无效或已过期");
            }
            AuthUser user = new AuthUser(
                    toLong(data.get("userId")),
                    text(data.get("username")),
                    text(data.get("name")),
                    asInt(data.get("roleId")),
                    false);
            if (cache.size() > CACHE_MAX_SIZE) {
                cache.clear();
            }
            cache.put(token, new CachedUser(user, now + CACHE_TTL_MILLIS));
            return user;
        } catch (HttpClientErrorException e) {
            throw AuthException.unauthorized("登录状态无效或已过期");
        } catch (RestClientException e) {
            throw AuthException.unavailable("认证服务暂不可用，请稍后重试");
        }
    }

    private static Integer asInt(Object value) {
        return value == null ? null : Integer.valueOf(value.toString());
    }

    private static Long toLong(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private record CachedUser(AuthUser user, long expiresAt) {
    }
}
