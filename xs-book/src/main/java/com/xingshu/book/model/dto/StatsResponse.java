package com.xingshu.book.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {

    private Long textbookCount;    // 教材总数
    private Long chunkCount;       // 切片总数
    private Long totalPages;       // 所有教材总页数之和
    private Long searchCount;      // 搜索次数（暂返回 0）
}