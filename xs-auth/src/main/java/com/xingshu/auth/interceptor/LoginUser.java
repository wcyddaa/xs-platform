package com.xingshu.auth.interceptor;

import java.lang.annotation.*;

/**
 * 注入当前登录用户信息到 Controller 方法参数
 * 参考 tjxt 项目的 UserContext 机制
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginUser {
}