package com.xingshu.book.model.dto;

import lombok.Data;

@Data
public class SearchRequest {

    private String query;          // 搜索文本（语义检索用）
    private Integer topK = 10;     // 返回 Top K 条结果，默认10
    private String textbookId;     // 可选：限定在指定教材内搜索
    private Integer startPage;     // 可选：起始页码（筛选切片 endPage >= startPage）
    private Integer endPage;       // 可选：结束页码（筛选切片 startPage <= endPage）
    private Double minScore;       // 可选：最低相似度分数阈值
}