package com.xingshu.book.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkListResult {

    private String chunkId;           // 切片ID
    private String textbookId;        // 所属教材ID
    private String textbookTitle;     // 教材标题
    private Integer chunkIndex;       // 切片序号
    private Integer startPage;        // 起始页码
    private Integer endPage;          // 结束页码
    private Integer paragraphIndex;   // 段落编号（该切片在起始页的第几个段落）
    private String content;           // 切片文本内容
    private Boolean hasMore;          // content 是否被截断（超过 200 字符时为 true）
}