package com.xingshu.book.model.dto;

import lombok.Data;

import java.util.List;

@Data
public class TextbookCreateRequest {

    private String title;
    private String isbn;
    private String author;
    private String category;
    private Integer totalPages;
    private String publishDate;
    private String fileType;
    private Long fileSize;

    /** 绑定教学班ID（教材按教学班归属） */
    private Long classId;

    /** 绑定教学班名称 */
    private String className;

    /** 教材切片列表（可选，也可以在创建教材后单独添加） */
    private List<ChunkItem> chunks;

    @Data
    public static class ChunkItem {
        private Integer chunkIndex;
        private Integer startPage;
        private Integer endPage;
        private Integer paragraphIndex;
        private String content;
    }
}