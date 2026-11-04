package com.xingshu.learning.domain.vo;

import lombok.Data;

/**
 * 学生提问统计VO
 */
@Data
public class StudentQuestionStatsVO {
    private Long studentId;
    private String studentName;
    private Long questionCount;
    private String topTopics;
}