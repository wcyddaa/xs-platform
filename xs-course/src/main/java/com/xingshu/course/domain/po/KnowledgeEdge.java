package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程知识图谱关系
 */
@Data
@TableName("knowledge_edge")
public class KnowledgeEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    /** 起点节点键 */
    private String sourceKey;

    /** 终点节点键 */
    private String targetKey;

    /** contains / prerequisite / related / weak_point */
    private String relation;

    /** 关系权重 0-1 */
    private BigDecimal weight;

    private LocalDateTime createTime;
}
