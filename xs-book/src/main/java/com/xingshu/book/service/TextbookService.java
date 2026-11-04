package com.xingshu.book.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.xingshu.common.base.PageResult;
import com.xingshu.book.model.dto.StatsResponse;
import com.xingshu.book.model.dto.TextbookCreateRequest;
import com.xingshu.book.model.entity.TextbookEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class TextbookService {

    private static final Logger log = LoggerFactory.getLogger(TextbookService.class);

    private final ElasticsearchClient elasticsearchClient;

    public TextbookService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 创建教材（仅元数据，不包含切片）
     */
    public TextbookEntity create(TextbookCreateRequest request) {
        TextbookEntity entity = new TextbookEntity();
        BeanUtils.copyProperties(request, entity);
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        // save 时实体必须指定 id，且 id 是 @Id 字段，不再写 @Field，所以直接用 ES client 写入
        try {
            elasticsearchClient.index(i -> i
                    .index("textbook_index")
                    .id(entity.getId())
                    .document(entity)
            );
        } catch (Exception e) {
            throw new RuntimeException("创建教材失败", e);
        }
        return entity;
    }

    /**
     * 根据ID查询教材
     */
    public Optional<TextbookEntity> findById(String id) {
        try {
            var response = elasticsearchClient.get(g -> g
                            .index("textbook_index")
                            .id(id),
                    TextbookEntity.class
            );
            return Optional.ofNullable(response.source());
        } catch (Exception e) {
            // ES get 在文档不存在时也会抛异常，返回空 Optional
            log.warn("查询教材 {} 失败（可能不存在）: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 绑定教材到教学班（classId 为 null 表示解绑）
     * 读取完整文档后整体回写，保证其它字段不被覆盖
     *
     * @return 更新后的教材；教材不存在时返回空
     */
    public Optional<TextbookEntity> bindClass(String id, Long classId, String className) {
        Optional<TextbookEntity> existing = findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TextbookEntity entity = existing.get();
        entity.setClassId(classId);
        entity.setClassName(classId != null ? className : null);
        entity.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        try {
            elasticsearchClient.index(i -> i
                    .index("textbook_index")
                    .id(id)
                    .document(entity)
            );
        } catch (Exception e) {
            throw new RuntimeException("更新教材教学班绑定失败", e);
        }
        return Optional.of(entity);
    }

    /**
     * 删除教材
     */
    public void deleteById(String id) {
        try {
            elasticsearchClient.delete(d -> d
                    .index("textbook_index")
                    .id(id)
            );
        } catch (Exception e) {
            throw new RuntimeException("删除教材失败", e);
        }
    }

    /**
     * 更新教材处理状态
     */
    public void updateStatus(String id, String status) {
        try {
            Map<String, Object> partial = new HashMap<>();
            partial.put("processStatus", status);
            partial.put("updateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            elasticsearchClient.update(u -> u
                    .index("textbook_index")
                    .id(id)
                    .doc(partial),
                    Map.class
            );
        } catch (Exception e) {
            log.error("更新教材状态失败: id={}, status={}", id, status, e);
        }
    }

    /**
     * 删除所有教材和切片（用于清空旧数据重新上传）
     * @return 删除的教材数量
     */
    public long deleteAll() {
        long deletedTextbooks = 0;
        long deletedChunks = 0;
        try {
            // 先删除所有切片
            try {
                var chunkResp = elasticsearchClient.deleteByQuery(d -> d
                        .index("textbook_chunk_vector")
                        .query(q -> q.matchAll(m -> m))
                        .refresh(true)
                        .conflicts(Conflicts.Proceed)
                );
                deletedChunks = chunkResp.deleted();
                log.info("已清空 textbook_chunk_vector 索引，删除 {} 个切片", deletedChunks);
            } catch (Exception e) {
                log.warn("清空切片索引失败（可能为空）: {}", e.getMessage());
            }

            // 再删除所有教材
            try {
                var response = elasticsearchClient.deleteByQuery(d -> d
                        .index("textbook_index")
                        .query(q -> q.matchAll(m -> m))
                        .refresh(true)
                        .conflicts(Conflicts.Proceed)
                );
                deletedTextbooks = response.deleted();
                log.info("已清空 textbook_index 索引，删除 {} 条教材", deletedTextbooks);
            } catch (Exception e) {
                log.warn("清空教材索引失败（可能为空）: {}", e.getMessage());
            }

            return deletedTextbooks;
        } catch (Exception e) {
            throw new RuntimeException("清空所有教材数据失败", e);
        }
    }

    /**
     * 获取切片索引中的文档总数（用于验证清空是否彻底）
     */
    public long countChunks() {
        try {
            return elasticsearchClient.count(c -> c
                    .index("textbook_chunk_vector")
            ).count();
        } catch (Exception e) {
            log.warn("统计切片数量失败: {}", e.getMessage());
            return -1;
        }
    }

    /**
     * 分页查询教材列表（支持关键词搜索 + 分类筛选）
     */
    public PageResult<TextbookEntity> search(String keyword, String category, Long classId,
                                             int page, int size,
                                             String sortField, String order) {
        try {
            // 检查索引是否存在
            boolean indexExists;
            try {
                indexExists = elasticsearchClient.indices().exists(e -> e.index("textbook_index")).value();
            } catch (Exception ex) {
                log.warn("检查 textbook_index 是否存在时出错，假定不存在: {}", ex.getMessage());
                indexExists = false;
            }

            if (!indexExists) {
                log.warn("textbook_index 不存在，返回空列表");
                return PageResult.of(List.of(), 0L, page, size);
            }

            List<Query> mustQueries = new ArrayList<>();
            List<Query> filterQueries = new ArrayList<>();

            if (keyword != null && !keyword.isBlank()) {
                mustQueries.add(Query.of(q -> q
                        .multiMatch(mm -> mm
                                .fields("title", "author")
                                .query(keyword)
                        )
                ));
            }

            if (category != null && !category.isBlank()) {
                // category 是 text 类型，做精确匹配需用 .keyword 子字段
                filterQueries.add(Query.of(q -> q
                        .term(t -> t.field("category.keyword").value(category))
                ));
            }

            if (classId != null) {
                filterQueries.add(Query.of(q -> q
                        .term(t -> t.field("classId").value(classId))
                ));
            }

            Query finalQuery;
            if (!mustQueries.isEmpty() || !filterQueries.isEmpty()) {
                finalQuery = Query.of(q -> q.bool(b -> {
                    if (!mustQueries.isEmpty()) b.must(mustQueries);
                    if (!filterQueries.isEmpty()) b.filter(filterQueries);
                    return b;
                }));
            } else {
                finalQuery = Query.of(q -> q.matchAll(m -> m));
            }

            // ES 动态映射可能导致排序字段类型不一致，改为在 Java 侧排序
            SearchResponse<JsonNode> response = elasticsearchClient.search(s -> s
                            .index("textbook_index")
                            .query(finalQuery)
                            .from((page - 1) * size)
                            .size(size),
                    JsonNode.class
            );

            long total = response.hits().total().value();
            List<TextbookEntity> list = response.hits().hits().stream()
                    .map(hit -> {
                        JsonNode source = hit.source();
                        TextbookEntity entity = new TextbookEntity();
                        entity.setId(hit.id());
                        entity.setTitle(getText(source, "title"));
                        entity.setIsbn(getText(source, "isbn"));
                        entity.setAuthor(getText(source, "author"));
                        entity.setCategory(getText(source, "category"));
                        entity.setClassId(getLong(source, "classId"));
                        entity.setClassName(getText(source, "className"));
                        entity.setTotalPages(getInt(source, "totalPages"));
                        entity.setPublishDate(getText(source, "publishDate"));
                        entity.setFileType(getText(source, "fileType"));
                        entity.setFileSize(getLong(source, "fileSize"));
                        entity.setCreateTime(getText(source, "createTime"));
                        entity.setUpdateTime(getText(source, "updateTime"));
                        entity.setProcessStatus(getText(source, "processStatus"));
                        return entity;
                    })
                    .toList();

            return PageResult.of(list, total, page, size);

        } catch (Exception e) {
            log.error("查询教材列表失败", e);
            throw new RuntimeException("查询教材列表失败", e);
        }
    }

    /**
     * 获取教材统计信息
     */
    public StatsResponse getStats() {
        try {
            long textbookCount = elasticsearchClient
                    .count(c -> c.index("textbook_index"))
                    .count();

            long chunkCount = 0;
            try {
                chunkCount = elasticsearchClient
                        .count(c -> c.index("textbook_chunk_vector"))
                        .count();
            } catch (Exception ignored) {
            }

            long totalPages = 0;
            try {
                SearchResponse<Void> sumResp = elasticsearchClient.search(s -> s
                                .index("textbook_index")
                                .size(0)
                                .aggregations("totalPagesSum", a -> a
                                        .sum(sa -> sa.field("totalPages"))
                                ),
                        Void.class
                );
                double sum = sumResp.aggregations().get("totalPagesSum").sum().value();
                totalPages = (long) sum;
            } catch (Exception ignored) {
            }

            return StatsResponse.builder()
                    .textbookCount(textbookCount)
                    .chunkCount(chunkCount)
                    .totalPages(totalPages)
                    .searchCount(0L)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("获取教材统计信息失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private String getText(JsonNode source, String field) {
        if (source == null || source.get(field) == null) return null;
        return source.get(field).asText();
    }

    private Integer getInt(JsonNode source, String field) {
        if (source == null || source.get(field) == null) return null;
        return source.get(field).asInt();
    }

    private Long getLong(JsonNode source, String field) {
        if (source == null || source.get(field) == null) return null;
        return source.get(field).asLong();
    }
}