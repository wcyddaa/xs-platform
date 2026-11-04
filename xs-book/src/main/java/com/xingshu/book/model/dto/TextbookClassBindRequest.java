package com.xingshu.book.model.dto;

import lombok.Data;

/**
 * 教材绑定教学班请求（classId 为 null 表示解绑）
 */
@Data
public class TextbookClassBindRequest {

    /** 教学班ID，null 表示解绑 */
    private Long classId;

    /** 教学班名称 */
    private String className;
}
