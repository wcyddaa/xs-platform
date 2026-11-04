package com.xingshu.auth.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功后返回的用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserDTO {
    /** 用户 ID */
    private Long userId;

    /** 用户名（工号） */
    private String username;

    /** 姓名 */
    private String name;

    /** 手机号/工号 */
    private String number;

    /** 角色 ID：1-管理员, 2-老师, 3-学生 */
    private Integer roleId;

    /** 角色名称 */
    private String roleName;
}