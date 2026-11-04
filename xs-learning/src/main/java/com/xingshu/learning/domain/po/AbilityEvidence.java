package com.xingshu.learning.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ability_evidence")
public class AbilityEvidence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long studentId;
    private Long dimensionId;
    private String sourceType;
    private Long sourceId;
    private BigDecimal score;
    private String evidenceText;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createrId;
    private Long updaterId;
}