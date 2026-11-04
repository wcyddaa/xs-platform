package com.xingshu.learning.domain.vo;

import lombok.Data;

/**
 * 提问倾向VO
 */
@Data
public class QuestionTendencyVO {
    private String topic;
    private Long count;
}