package com.xingshu.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级作业关联表（班级绑定模板作业）
 */
@Data
@TableName("class_homework")
public class ClassHomework {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 班级ID */
    private Long classId;

    /** 班级名称（冗余） */
    private String className;

    /** 模板作业ID */
    private Long templateId;

    /** 分配时间 */
    private LocalDateTime assignTime;
}