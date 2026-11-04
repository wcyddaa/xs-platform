package com.xingshu.learning.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生学情报告（按教学班隔离）
 */
@Data
@TableName("student_ability_report")
public class StudentAbilityReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long classId;
    private BigDecimal overallScore;
    private String summary;
    private String source;
    private Integer questionCount;
    private Integer homeworkCount;
    private BigDecimal homeworkAvgScore;
    private LocalDateTime evaluatedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createrId;
    private Long updaterId;
}
