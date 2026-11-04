package com.xingshu.learning.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("ability_dimension")
public class AbilityDimension {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classId;
    private Long chapterId;
    private String dimensionName;
    private String dimensionCode;
    private BigDecimal weight;
    private String description;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Long createrId;
    private Long updaterId;
}