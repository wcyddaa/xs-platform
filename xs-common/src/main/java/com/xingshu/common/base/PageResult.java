package com.xingshu.common.base;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 */
@Data
public class PageResult<T> {

    private List<T> list;
    private Long total;
    private Integer page;
    private Integer size;
    private Integer totalPages;

    public static <T> PageResult<T> of(List<T> list, Long total, Integer page, Integer size) {
        PageResult<T> result = new PageResult<>();
        result.setList(list);
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setTotalPages((int) Math.ceil((double) total / size));
        return result;
    }
}