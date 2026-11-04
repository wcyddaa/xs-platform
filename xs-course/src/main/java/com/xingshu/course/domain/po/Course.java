package com.xingshu.course.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 课程信息 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course")
public class Course extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程名称 */
    private String name;

    /** 课程描述 */
    private String description;

    /** 教材ID（关联 xs-book 的教材） */
    private String textbookId;

    /** 教材名称（冗余） */
    private String textbookTitle;

    /** 课程状态：draft-草稿, pending-待审核, approved-已通过 */
    private String status;
}