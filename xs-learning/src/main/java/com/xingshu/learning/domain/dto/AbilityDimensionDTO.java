package com.xingshu.learning.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 教学班能力维度设置参数
 */
@Data
public class AbilityDimensionDTO {
    private Long id;
    private Long classId;
    /** 维度名称 */
    private String dimensionName;
    /** 维度编码（同教学班内唯一） */
    private String dimensionCode;
    private BigDecimal weight;
    private String description;
    private Integer sortOrder;
    private Long teacherId;
}
