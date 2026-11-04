package com.xingshu.user.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 行政班学生关联表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_class_student")
public class AdminClassStudent extends BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 行政班ID */
    private Long classId;

    /** 学生ID */
    private Long studentId;
}