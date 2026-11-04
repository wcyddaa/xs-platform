package com.xingshu.book.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@Document(indexName = "courseware_index")
public class CoursewareEntity {

    @Id
    private String id;                     // 课件唯一ID，UUID

    @Field(type = FieldType.Integer)
    private Integer courseId;              // 关联课程ID

    @Field(type = FieldType.Keyword)
    private String courseName;             // 关联课程名称

    @Field(type = FieldType.Long)
    private Long chapterId;                // 关联章节ID（课件挂在课程章节下）

    @Field(type = FieldType.Text, analyzer = "ik_max_word")
    private String title;                  // 课件标题

    @Field(type = FieldType.Keyword)
    private String fileType;               // 文件类型

    @Field(type = FieldType.Long)
    private Long fileSize;                 // 文件大小

    @Field(type = FieldType.Integer)
    private Integer totalPages;            // 总页数

    @Field(type = FieldType.Keyword)
    private String processStatus;          // 处理状态：processing/completed/failed

    @Field(type = FieldType.Integer)
    private Integer createrId;             // 创建者（课程负责人）

    @Field(type = FieldType.Keyword)
    private String createrName;            // 创建者姓名

    @Field(type = FieldType.Keyword)
    private String createTime;             // 创建时间

    @Field(type = FieldType.Keyword)
    private String updateTime;             // 更新时间
}