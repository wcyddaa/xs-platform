package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教学班信息
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("class_info")
public class ClassInfo extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教学班名称 */
    private String name;

    /** 教学班描述 */
    private String description;

    /** 所属学院 */
    private String college;

    /** 绑定教师ID */
    private Long teacherId;

    /** 教师姓名（冗余） */
    private String teacherName;

    /** 状态：draft-草稿, pending-待审核, approved-已通过 */
    private String status;

    /** 学生人数（非数据库字段，列表查询时填充） */
    @TableField(exist = false)
    private Long studentCount;

    /** 绑定教师姓名，多个以逗号分隔（非数据库字段） */
    @TableField(exist = false)
    private String teacherNames;
}