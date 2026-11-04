package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 教学班-教师关联（多对多）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("class_teacher")
public class ClassTeacher extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教学班ID */
    private Long classId;

    /** 教师ID（xs-user 中 type=2） */
    private Long teacherId;

    /** 教师姓名（冗余） */
    private String teacherName;

    /** 是否主教师：1-是，0-否 */
    private Integer isOwner;

    /** 绑定时间 */
    private LocalDateTime joinTime;
}
