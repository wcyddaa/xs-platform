package com.xingshu.common.constant;

/**
 * 通用常量
 */
public final class CommonConstants {

    private CommonConstants() {}

    /** 用户类型：管理员 */
    public static final int USER_TYPE_ADMIN = 1;
    /** 用户类型：老师 */
    public static final int USER_TYPE_TEACHER = 2;
    /** 用户类型：学生 */
    public static final int USER_TYPE_STUDENT = 3;

    /** 课程状态：草稿 */
    public static final String COURSE_STATUS_DRAFT = "draft";
    /** 课程状态：待审核 */
    public static final String COURSE_STATUS_PENDING = "pending";
    /** 课程状态：已通过 */
    public static final String COURSE_STATUS_APPROVED = "approved";

    /** 作业状态：待提交 */
    public static final String HOMEWORK_STATUS_PENDING = "pending";
    /** 作业状态：已提交 */
    public static final String HOMEWORK_STATUS_SUBMITTED = "submitted";
    /** 作业状态：已批改 */
    public static final String HOMEWORK_STATUS_GRADED = "graded";

    /** 模板作业状态：草稿 */
    public static final String TEMPLATE_STATUS_DRAFT = "draft";
    /** 模板作业状态：已发布 */
    public static final String TEMPLATE_STATUS_PUBLISHED = "published";
}