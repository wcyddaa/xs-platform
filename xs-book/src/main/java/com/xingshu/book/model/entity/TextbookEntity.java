package com.xingshu.book.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Data
@Document(indexName = "textbook_index")
public class TextbookEntity {

    @Id
    private String id;                     // 教材唯一ID，使用UUID（移除 @Field，避免与 @Id 冲突）

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;                  // 教材标题

    @Field(type = FieldType.Keyword)
    private String isbn;                   // ISBN号

    @Field(type = FieldType.Keyword)
    private String author;                 // 作者

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String category;               // 学科分类

    @Field(type = FieldType.Long)
    private Long classId;                  // 绑定教学班ID（教材按教学班归属）

    @Field(type = FieldType.Keyword)
    private String className;              // 绑定教学班名称（冗余）

    @Field(type = FieldType.Integer)
    private Integer totalPages;            // 总页数

    @Field(type = FieldType.Keyword)
    private String publishDate;            // 出版日期（存为 keyword 而非 date，避免类型转换警告）

    @Field(type = FieldType.Keyword)
    private String fileType;               // 文件类型

    @Field(type = FieldType.Long)
    private Long fileSize;                 // 文件大小

    @Field(type = FieldType.Keyword)
    private String createTime;             // 入库时间（字符串格式）

    @Field(type = FieldType.Keyword)
    private String updateTime;             // 更新时间（字符串格式）

    @Field(type = FieldType.Keyword)
    private String processStatus;          // 处理状态：processing（解析中）/ completed（已完成）/ failed（失败）

}