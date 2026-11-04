package com.xingshu.learning.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 学生在某个教学班下的能力画像（学生详情页聚合数据）
 */
@Data
public class AbilityProfileVO {
    private Long studentId;
    private Long classId;
    private String studentName;
    private ReportVO report;
    private List<DimensionScoreVO> dimensions = new ArrayList<>();
    private QuestionSummaryVO questions;
    private HomeworkSummaryVO homework;

    @Data
    public static class ReportVO {
        private BigDecimal overallScore;
        private String summary;
        private String source;
        private LocalDateTime evaluatedAt;
        private Integer questionCount;
        private Integer homeworkCount;
        private BigDecimal homeworkAvgScore;
    }

    @Data
    public static class DimensionScoreVO {
        private Long dimensionId;
        private String dimensionName;
        private String dimensionCode;
        private BigDecimal weight;
        private String description;
        private Integer sortOrder;
        private BigDecimal score;
        private String comment;
        private Integer evidenceCount;
    }

    @Data
    public static class QuestionSummaryVO {
        private Integer count;
        private List<TopicCountVO> topTopics = new ArrayList<>();
    }

    @Data
    public static class TopicCountVO {
        private String topic;
        private Long count;
    }

    @Data
    public static class HomeworkSummaryVO {
        private Integer count;
        private Integer gradedCount;
        private Integer submittedCount;
        private BigDecimal avgScore;
        private List<HomeworkItemVO> items = new ArrayList<>();
    }

    @Data
    public static class HomeworkItemVO {
        private Long templateId;
        private String title;
        private Double score;
        private String status;
        private String comment;
        private LocalDateTime submitTime;
    }
}
