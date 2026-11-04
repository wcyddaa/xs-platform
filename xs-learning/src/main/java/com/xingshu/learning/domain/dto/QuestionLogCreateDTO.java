package com.xingshu.learning.domain.dto;

import lombok.Data;

/**
 * 提问记录创建DTO
 */
@Data
public class QuestionLogCreateDTO {
    private Long classId;
    private Long studentId;
    private String studentName;
    private String question;
    private String answer;
    private String topics;
    private Integer tokensUsed;
}