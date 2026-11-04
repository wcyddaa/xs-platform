package com.xingshu.auth.common.exception;

import cn.hutool.core.exceptions.ValidateException;
import com.xingshu.common.base.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 参考 tjxt 项目 CommonExceptionAdvice
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 认证异常 */
    @ExceptionHandler(AuthException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleAuthException(AuthException e) {
        log.warn("认证异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** Hutool 校验异常（JWT校验失败） */
    @ExceptionHandler(ValidateException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidateException(ValidateException e) {
        log.warn("校验异常: {}", e.getMessage());
        return ApiResponse.error(401, e.getMessage());
    }

    /** 参数校验异常 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验异常: {}", message);
        return ApiResponse.error(400, message);
    }

    /** 运行时异常兜底 */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return ApiResponse.error(500, "服务器内部错误");
    }
}