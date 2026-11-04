package com.xingshu.learning.domain.dto;

import lombok.Data;

/**
 * 提问日志查询DTO
 */
@Data
public class QuestionLogQueryDTO {
    private Long classId;
    private Long studentId;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}