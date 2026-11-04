package com.xingshu.learning.domain.vo;

import lombok.Data;

/**
 * 教学班提问统计VO
 */
@Data
public class ClassQuestionStatsVO {
    private Long classId;
    private Long totalQuestions;
    private Long studentCount;
    private String topTopics;
}