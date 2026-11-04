package com.xingshu.auth.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体（与 xs-user 中的 User 表一致）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账号（学号/工号） */
    private String username;

    /** 姓名 */
    private String name;

    /** 密码 */
    private String password;

    /** 手机号 */
    private String number;

    /** 账户状态：0-禁用，1-正常 */
    private Integer status;

    /** 身份类型：1-管理员，2-老师，3-学生 */
    private Integer type;
}