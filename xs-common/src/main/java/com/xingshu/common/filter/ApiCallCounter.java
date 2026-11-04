package com.xingshu.common.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * API 每日调用计数器（仅在有 Redis 的服务中生效）
 * <p>
 * 使用 @ConditionalOnClass 确保 StringRedisTemplate 不在 classpath 时整个 Bean 不被加载
 */
@Component
@ConditionalOnClass(StringRedisTemplate.class)
public class ApiCallCounter {

    private static final Logger log = LoggerFactory.getLogger(ApiCallCounter.class);
    private static final String API_COUNT_KEY_PREFIX = "api:count:daily:";
    private static final Duration API_COUNT_TTL = Duration.ofHours(48);

    private final StringRedisTemplate redisTemplate;

    public ApiCallCounter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void increment() {
        try {
            String todayKey = API_COUNT_KEY_PREFIX + LocalDate.now().format(DateTimeFormatter.ISO_DATE);
            redisTemplate.opsForValue().increment(todayKey);
            redisTemplate.expire(todayKey, API_COUNT_TTL);
        } catch (Exception e) {
            log.debug("API 计数失败: {}", e.getMessage());
        }
    }
}