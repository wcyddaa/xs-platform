package com.xingshu.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学生提问记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("student_question_log")
public class StudentQuestionLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教学班ID */
    private Long classId;

    /** 学生ID */
    private Long studentId;

    /** 学生姓名 */
    private String studentName;

    /** 提问内容 */
    private String question;

    /** AI回答内容 */
    private String answer;

    /** 提问主题/倾向标签 */
    private String topics;

    /** 消耗Token数 */
    private Integer tokensUsed;
}