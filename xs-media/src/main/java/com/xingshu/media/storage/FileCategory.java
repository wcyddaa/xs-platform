package com.xingshu.media.storage;

/**
 * 平台文件分类。
 * <p>
 * 所有业务模块（教材、课件、作业附件）统一使用这里的分类，
 * 本地目录与云端对象前缀保持一致，避免各模块各自维护一套 uploads 路径。
 */
public enum FileCategory {

    /** 教材：绑定教学班，路径 textbooks/ */
    TEXTBOOK("textbooks"),

    /** 课件：绑定课程/章节，路径 courseware/ */
    COURSEWARE("courseware"),

    /** 作业附件：绑定作业，路径 homework/ */
    HOMEWORK("homework");

    private final String dir;

    FileCategory(String dir) {
        this.dir = dir;
    }

    public String getDir() {
        return dir;
    }
}
