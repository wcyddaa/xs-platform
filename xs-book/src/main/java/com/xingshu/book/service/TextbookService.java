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
     * 提取教材目录摘要（扫描前若干页的目录文本，解析章节标题）
     *
     * 供 AI 在对话开始时就"知道这本书大概讲什么"，而不必先做向量检索。
     */
    public List<String> extractOutline(String textbookId, int scanPages) {
        List<String> chapters = new java.util.ArrayList<>();
        try {
            SearchResponse<JsonNode> response = elasticsearchClient.search(s -> s
                    .index("textbook_chunk_vector")
                    .query(q -> q.bool(b -> b
                            .filter(f -> f.term(t -> t.field("metadata.textbookId").value(textbookId)))
                            .filter(f -> f.range(r -> r.number(n -> n
                                    .field("metadata.startPage")
                                    .lte((double) scanPages))))))
                    .sort(so -> so.field(fs -> fs
                            .field("metadata.chunkIndex")
                            .order(co.elastic.clients.elasticsearch._types.SortOrder.Asc)))
                    .size(120),
                    JsonNode.class);

            // 目录行形如「3.7 softmax回归的简洁实现 . . . 123」
            java.util.regex.Pattern anyLevel = java.util.regex.Pattern.compile(
                    "^\\s*(\\d{1,2}(?:\\.\\d+){0,2})\\s+([^\\s.].{1,38}?)\\s*(?:\\.\\s*){2,}.*$");
            // 只取一级章标题：形如「3 线性神经网络 85」或「3 线性神经网络 . . . 85」
            java.util.regex.Pattern topLevel = java.util.regex.Pattern.compile(
                    "^\\s*(\\d{1,2})\\s+([^\\s.][^\\r\\n]{1,40}?)\\s+(?:(?:\\.\\s*){2,})?\\d{1,4}\\s*$");

            java.util.LinkedHashSet<String> topChapters = new java.util.LinkedHashSet<>();
            java.util.LinkedHashSet<String> allEntries = new java.util.LinkedHashSet<>();

            for (var hit : response.hits().hits()) {
                JsonNode source = hit.source();
                if (source == null || !source.hasNonNull("content")) {
                    continue;
                }
                for (String line : source.get("content").asText().split("\\r?\\n")) {
                    var all = anyLevel.matcher(line);
                    if (all.matches()) {
                        allEntries.add(all.group(1) + " " + all.group(2).trim());
                    }
                    var top = topLevel.matcher(line);
                    if (top.matches()) {
                        topChapters.add(top.group(1) + " " + top.group(2).trim());
                    }
                }
            }
            // 一级章节足够多时优先用它，否则退回全部层级
            java.util.LinkedHashSet<String> chosen = topChapters.size() >= 3 ? topChapters : allEntries;
            // 章号必须递增：过滤正文里偶然匹配到的「5 定义训练函数」这类噪声
            int lastChapterNo = 0;
            for (String entry : chosen) {
                int chapterNo;
                try {
                    chapterNo = Integer.parseInt(entry.split(" ", 2)[0]);
                } catch (NumberFormatException ex) {
                    continue;
                }
                if (chapterNo <= lastChapterNo) {
                    continue;
                }
                lastChapterNo = chapterNo;
                chapters.add(entry);
                if (chapters.size() >= 20) {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("解析教材 {} 目录失败: {}", textbookId, e.getMessage());
        }
        return chapters;
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
     * 绑定教材到教学班（多对多）：已绑定的班级不受影响，重复绑定幂等
     *
     * @return 更新后的教材；教材不存在时返回空
     */
    public Optional<TextbookEntity> bindClass(String id, Long classId, String className) {
        Optional<TextbookEntity> existing = findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TextbookEntity entity = existing.get();
        normalizeBindings(entity);
        if (classId != null && !entity.getClassIds().contains(classId)) {
            entity.getClassIds().add(classId);
            entity.getClassNames().add(className == null ? "" : className);
        }
        applyBindings(entity);
        return save(entity);
    }

    /** 解除教材与某个教学班的绑定，其它班级保留 */
    public Optional<TextbookEntity> unbindClass(String id, Long classId) {
        Optional<TextbookEntity> existing = findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TextbookEntity entity = existing.get();
        normalizeBindings(entity);
        int index = entity.getClassIds().indexOf(classId);
        if (index >= 0) {
            entity.getClassIds().remove(index);
            if (index < entity.getClassNames().size()) {
                entity.getClassNames().remove(index);
            }
        }
        applyBindings(entity);
        return save(entity);
    }

    /** 教学班被删除时，解除所有教材对该班的绑定（多对多只删绑定关系） */
    public int unbindAllForClass(Long classId) {
        int affected = 0;
        try {
            // 先刷新索引，避免刚写入的绑定关系查不到
            elasticsearchClient.indices().refresh(r -> r.index("textbook_index"));
            var response = elasticsearchClient.search(s -> s
                            .index("textbook_index")
                            .query(q -> q.bool(b -> b
                                    .should(s1 -> s1.terms(t -> t.field("classIds")
                                            .terms(v -> v.value(List.of(co.elastic.clients.elasticsearch._types.FieldValue.of(classId))))))
                                    .should(s2 -> s2.term(t -> t.field("classId").value(classId)))
                                    .minimumShouldMatch("1")))
                            .size(1000),
                    JsonNode.class
            );
            for (var hit : response.hits().hits()) {
                TextbookEntity entity = findById(hit.id()).orElse(null);
                if (entity == null) {
                    continue;
                }
                normalizeBindings(entity);
                int index = entity.getClassIds().indexOf(classId);
                if (index >= 0) {
                    entity.getClassIds().remove(index);
                    if (index < entity.getClassNames().size()) {
                        entity.getClassNames().remove(index);
                    }
                    applyBindings(entity);
                    save(entity);
                    affected++;
                }
            }
        } catch (Exception e) {
            log.warn("解除教学班 {} 的教材绑定失败: {}", classId, e.getMessage());
        }
        return affected;
    }

    /** 修改教材名称 */
    public Optional<TextbookEntity> updateTitle(String id, String title) {
        Optional<TextbookEntity> existing = findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        TextbookEntity entity = existing.get();
        entity.setTitle(title);
        return save(entity);
    }

    /** 旧数据只有单个 classId，统一迁移到 classIds/classNames */
    private void normalizeBindings(TextbookEntity entity) {
        if (entity.getClassIds() == null) {
            entity.setClassIds(new ArrayList<>());
        }
        if (entity.getClassNames() == null) {
            entity.setClassNames(new ArrayList<>());
        }
        if (entity.getClassId() != null) {
            if (!entity.getClassIds().contains(entity.getClassId())) {
                entity.getClassIds().add(entity.getClassId());
                entity.getClassNames().add(entity.getClassName() == null ? "" : entity.getClassName());
            }
            entity.setClassId(null);
        }
    }

    private void applyBindings(TextbookEntity entity) {
        entity.setClassName(entity.getClassNames().isEmpty() ? null : String.join("、", entity.getClassNames()));
        entity.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private Optional<TextbookEntity> save(TextbookEntity entity) {
        try {
            elasticsearchClient.index(i -> i
                    .index("textbook_index")
                    .id(entity.getId())
                    .refresh(co.elastic.clients.elasticsearch._types.Refresh.WaitFor)
                    .document(entity)
            );
        } catch (Exception e) {
            throw new RuntimeException("更新教材失败", e);
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
                // 多对多：命中 classIds 数组，同时兼容仅有单个 classId 的旧文档
                filterQueries.add(Query.of(q -> q
                        .bool(b -> b
                                .should(s1 -> s1.terms(t -> t.field("classIds")
                                        .terms(v -> v.value(List.of(co.elastic.clients.elasticsearch._types.FieldValue.of(classId))))))
                                .should(s2 -> s2.term(t -> t.field("classId").value(classId)))
                                .minimumShouldMatch("1"))
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
                        entity.setClassIds(getLongList(source, "classIds"));
                        entity.setClassNames(getTextList(source, "classNames"));
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

    private List<Long> getLongList(JsonNode source, String field) {
        if (source == null || source.get(field) == null || !source.get(field).isArray()) return null;
        List<Long> list = new ArrayList<>();
        source.get(field).forEach(node -> list.add(node.asLong()));
        return list;
    }

    private List<String> getTextList(JsonNode source, String field) {
        if (source == null || source.get(field) == null || !source.get(field).isArray()) return null;
        List<String> list = new ArrayList<>();
        source.get(field).forEach(node -> list.add(node.asText()));
        return list;
    }
}