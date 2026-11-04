package com.xingshu.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 用户表
 * </p>
 *
 * @author 虎哥
 * @since 2026-05-23
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 账号(学号/工号)
     */
    private String username;

    /**
     * 姓名
     */
    private String name;

    /**
     * 密码
     */
    private String password;

    /**
     * 手机号
     */
    private String number;

    /**
     * 账户状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 身份：1-管理员，2-老师，3-学生
     */
    private Integer type;

    /** 行政班ID（非数据库字段，仅用于创建学生时传入） */
    @TableField(exist = false)
    private Long classId;
}