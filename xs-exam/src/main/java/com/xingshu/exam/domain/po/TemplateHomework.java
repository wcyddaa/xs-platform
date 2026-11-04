package com.xingshu.exam.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 模板作业（老师发布，一个模板对应多个学生实例作业） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("template_homework")
public class TemplateHomework extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属课程ID */
    private Long courseId;

    /** 所属教学班ID */
    private Long classId;

    /** 关联章节ID（可选，可关联到具体章节） */
    private Long chapterId;

    /** 指定教材起始页码 */
    private Integer startPage;

    /** 指定教材结束页码 */
    private Integer endPage;

    /** 作业标题 */
    private String title;

    /** 作业描述/内容 */
    private String description;

    /** Markdown格式作业文件内容 */
    private String markdownContent;

    /** 附件列表（JSON数组：[{name, type, content}]） */
    private String attachments;

    /** 发布老师ID */
    private Long teacherId;

    /** 发布老师姓名 */
    private String teacherName;

    /** 状态：draft-草稿, published-已发布 */
    private String status;

    /** 作业发布时教学班总人数 */
    private Integer totalStudents;

    /** 已提交人数 */
    private Integer submittedCount;

    /** 已批改人数 */
    private Integer gradedCount;
}