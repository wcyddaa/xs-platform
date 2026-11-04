package com.xingshu.learning.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("student_ability")
public class StudentAbility {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long classId;
    private Long dimensionId;
    private BigDecimal masteryLevel;
    private Integer evidenceCount;
    /** 智能体针对该维度的评语 */
    private String comment;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createrId;
    private Long updaterId;
}