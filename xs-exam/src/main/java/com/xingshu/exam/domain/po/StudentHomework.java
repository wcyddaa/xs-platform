package com.xingshu.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学生实例作业（每个学生针对模板作业的作答）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_homework")
public class StudentHomework extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板作业ID */
    private Long templateId;

    /** 学生ID */
    private Long studentId;

    /** 学生姓名（冗余） */
    private String studentName;

    /** 作答内容 */
    private String answer;

    /** 状态：pending-待提交, submitted-已提交, graded-已批改 */
    private String status;

    /** 分数 */
    private Double score;

    /** 老师评语 */
    private String comment;

    /** 提交时间 */
    private LocalDateTime submitTime;
}