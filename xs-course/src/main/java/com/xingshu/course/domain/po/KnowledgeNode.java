package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程知识图谱节点
 */
@Data
@TableName("knowledge_node")
public class KnowledgeNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 节点唯一键（课程内唯一） */
    private String nodeKey;

    /** 节点名称 */
    private String name;

    /** chapter / courseware / question / homework / concept */
    private String category;

    /** 节点说明 */
    private String description;

    /** 关联章节ID */
    private Long chapterId;

    /** 证据权重 0-1 */
    private BigDecimal weight;

    /** 主要来源 */
    private String source;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
