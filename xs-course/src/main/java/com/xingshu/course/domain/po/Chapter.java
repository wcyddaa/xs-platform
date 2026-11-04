package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程章节（课程子表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chapter")
public class Chapter extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 章节标题 */
    private String title;

    /** 章节描述 */
    private String description;

    /** 排序序号 */
    private Integer sortOrder;

    /** 对应教材起始页码 */
    private Integer startPage;

    /** 对应教材结束页码 */
    private Integer endPage;
}