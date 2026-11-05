package com.xingshu.user.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.xingshu.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * xs-user 基础配置：分页插件 + 统一异常输出。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    /** 参数/业务异常统一返回 400，避免前端拿到无提示的 500 */
    @RestControllerAdvice
    public static class UserExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(UserExceptionHandler.class);

        @ExceptionHandler(IllegalArgumentException.class)
        public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }

        @ExceptionHandler(RuntimeException.class)
        public ApiResponse<Void> handleRuntime(RuntimeException e) {
            log.warn("用户服务处理失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        }
    }
}
