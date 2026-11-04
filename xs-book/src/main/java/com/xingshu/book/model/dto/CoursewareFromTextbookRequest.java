package com.xingshu.book.model.dto;

import lombok.Data;

/**
 * 由教材页范围生成课件的请求
 */
@Data
public class CoursewareFromTextbookRequest {

    /** 教材ID */
    private String textbookId;

    /** 起始页（从 1 开始，含） */
    private Integer startPage;

    /** 结束页（从 1 开始，含） */
    private Integer endPage;

    /** 课件标题（可选，默认「教材标题 · 第x-y页」） */
    private String title;

    /** 关联课程ID */
    private Integer courseId;

    /** 关联课程名称 */
    private String courseName;

    /** 关联章节ID */
    private Long chapterId;

    /** 创建者ID */
    private Integer createrId;

    /** 创建者姓名 */
    private String createrName;
}
