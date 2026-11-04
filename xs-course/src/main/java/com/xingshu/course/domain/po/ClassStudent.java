package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 班级-学生关联表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("class_student")
public class ClassStudent extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级ID */
    private Long classId;

    /** 学生ID（关联 xs-user 的 user 表，type=3） */
    private Long studentId;

    /** 学生姓名（冗余） */
    private String studentName;

    /** 学生学号/用户名（非数据库字段，从 xs-user 查询填充） */
    @TableField(exist = false)
    private String studentAccount;

    /** 加入时间 */
    private LocalDateTime joinTime;
}