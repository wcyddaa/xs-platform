package com.xingshu.book.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Map;

/**
 * 教材切片实体，用于 Spring AI VectorStore 的向量存储和检索。
 * 此索引由 Spring AI 自动创建和管理，无需手动维护 mapping。
 */
@Data
public class TextbookChunkEntity {

    @Id
    @Field(type = FieldType.Keyword)
    private String chunkId;                // 切片唯一ID

    @Field(type = FieldType.Keyword)
    private String textbookId;             // 所属教材ID

    @Field(type = FieldType.Keyword)
    private String textbookTitle;          // 教材标题（冗余，方便检索结果展示）

    @Field(type = FieldType.Integer)
    private Integer chunkIndex;            // 切片序号

    @Field(type = FieldType.Integer)
    private Integer startPage;             // 起始页码

    @Field(type = FieldType.Integer)
    private Integer endPage;               // 结束页码

    @Field(type = FieldType.Text)
    private String content;                // 切片文本内容

    @Field(type = FieldType.Dense_Vector, dims = 1024)
    private float[] embedding;             // 文本向量

    @Field(type = FieldType.Object)
    private Map<String, Object> extraMeta; // 扩展元数据
}