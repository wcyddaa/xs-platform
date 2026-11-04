package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 课程-班级关联表（多对多）
 */
@Data
@TableName("course_class")
public class CourseClass {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID */
    private Long courseId;

    /** 班级ID */
    private Long classId;

    /** 班级名称（冗余） */
    private String className;
}