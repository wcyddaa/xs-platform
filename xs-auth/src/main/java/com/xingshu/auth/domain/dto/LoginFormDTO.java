package com.xingshu.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 登录表单 DTO
 */
@Data
public class LoginFormDTO {
    /** 用户名（学号/工号） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录身份：1-管理员, 2-老师, 3-学生 */
    @NotNull(message = "登录身份不能为空")
    private Integer type;
}