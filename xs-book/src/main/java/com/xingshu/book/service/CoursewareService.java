package com.xingshu.book.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.xingshu.book.model.dto.SearchRequest;
import com.xingshu.book.model.dto.SearchResult;
import com.xingshu.book.model.entity.CoursewareEntity;
import com.xingshu.common.base.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class CoursewareService {

    private static final Logger log = LoggerFactory.getLogger(CoursewareService.class);

    private final ElasticsearchClient elasticsearchClient;
    private final EmbeddingModel embeddingModel;
    private final FileStorageService fileStorageService;

    public CoursewareService(ElasticsearchClient elasticsearchClient,
                             EmbeddingModel embeddingModel,
                             FileStorageService fileStorageService) {
        this.elasticsearchClient = elasticsearchClient;
        this.embeddingModel = embeddingModel;
        this.fileStorageService = fileStorageService;
    }

    // ==================== 课件CRUD ====================

    /** 创建课件元数据 */
    public CoursewareEntity create(CoursewareEntity entity) {
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setProcessStatus("processing");
        try {
            elasticsearchClient.index(i -> i
                    .index("courseware_index")
                    .id(entity.getId())
                    .document(entity)
            );
        } catch (Exception e) {
            throw new RuntimeException("创建课件失败", e);
        }
        return entity;
    }

    /** 根据ID查询课件 */
    public Optional<CoursewareEntity> findById(String id) {
        try {
            var response = elasticsearchClient.get(g -> g
                            .index("courseware_index")
                            .id(id),
                    CoursewareEntity.class
            );
            return Optional.ofNullable(response.source());
        } catch (Exception e) {
            log.warn("查询课件 {} 失败: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    /** 更新课件状态 */
    public void updateStatus(String id, String status) {
        try {
            Map<String, Object> partial = new HashMap<>();
            partial.put("processStatus", status);
            partial.put("updateTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            elasticsearchClient.update(u -> u
                    .index("courseware_index")
                    .id(id)
                    .doc(partial),
                    Map.class
            );
        } catch (Exception e) {
            log.error("更新课件状态失败: id={}, status={}", id, status, e);
        }
    }

    /** 删除课件及其所有切片、以及存储中的 PDF 文件 */
    public void deleteById(String id) {
        try {
            // 先删除切片
            try {
                elasticsearchClient.deleteByQuery(d -> d
                        .index("courseware_chunk_vector")
                        .query(q -> q.term(t -> t.field("metadata.coursewareId.keyword").value(id)))
                        .refresh(true)
                        .conflicts(Conflicts.Proceed)
                );
            } catch (Exception e) {
                log.warn("删除课件切片失败: {}", e.getMessage());
            }
            // 再删除课件
            elasticsearchClient.delete(d -> d
                    .index("courseware_index")
                    .id(id)
            );
        } catch (Exception e) {
            throw new RuntimeException("删除课件失败", e);
        }
        // 元数据删除成功后再清理文件，失败只记录日志，避免影响删除结果
        try {
            fileStorageService.deleteCourseware(id);
        } catch (Exception e) {
            log.warn("删除课件文件失败: id={}, {}", id, e.getMessage());
        }
    }

    /** 按课程批量删除课件（课程删除时级联） */
    public int deleteByCourse(Integer courseId) {
        return deleteByQuery(Query.of(q -> q.term(t -> t.field("courseId").value(courseId))));
    }

    /** 按章节批量删除课件（章节删除时级联） */
    public int deleteByChapter(Long chapterId) {
        return deleteByQuery(Query.of(q -> q.term(t -> t.field("chapterId").value(chapterId))));
    }

    private int deleteByQuery(Query query) {
        try {
            List<String> ids = new ArrayList<>();
            var response = elasticsearchClient.search(s -> s
                            .index("courseware_index")
                            .query(query)
                            .size(1000),
                    JsonNode.class
            );
            for (var hit : response.hits().hits()) {
                ids.add(hit.id());
            }
            if (ids.isEmpty()) {
                return 0;
            }
            // 先删这些课件对应的切片
            try {
                List<co.elastic.clients.elasticsearch._types.FieldValue> values = ids.stream()
                        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                        .toList();
                elasticsearchClient.deleteByQuery(d -> d
                        .index("courseware_chunk_vector")
                        .query(q -> q.terms(t -> t.field("metadata.coursewareId.keyword").terms(v -> v.value(values))))
                        .refresh(true)
                        .conflicts(Conflicts.Proceed)
                );
            } catch (Exception e) {
                log.warn("批量删除课件切片失败: {}", e.getMessage());
            }
            elasticsearchClient.deleteByQuery(d -> d
                    .index("courseware_index")
                    .query(query)
                    .refresh(true)
                    .conflicts(Conflicts.Proceed)
            );
            for (String id : ids) {
                try {
                    fileStorageService.deleteCourseware(id);
                } catch (Exception e) {
                    log.warn("删除课件文件失败: id={}, {}", id, e.getMessage());
                }
            }
            return ids.size();
        } catch (Exception e) {
            log.warn("批量删除课件失败: {}", e.getMessage());
            return 0;
        }
    }

    /** 分页查询某课程（可选某章节）的课件列表 */
    public PageResult<CoursewareEntity> listByCourse(Integer courseId, Long chapterId, int page, int size) {
        try {
            // 检查索引是否存在
            boolean indexExists;
            try {
                indexExists = elasticsearchClient.indices().exists(e -> e.index("courseware_index")).value();
            } catch (Exception ex) {
                log.warn("检查 courseware_index 是否存在时出错，假定不存在: {}", ex.getMessage());
                indexExists = false;
            }

            if (!indexExists) {
                log.warn("courseware_index 不存在，返回空列表");
                return PageResult.of(List.of(), 0L, page, size);
            }

            Query query = Query.of(q -> q.bool(b -> {
                b.filter(f -> f.term(t -> t.field("courseId").value(courseId)));
                if (chapterId != null) {
                    b.filter(f -> f.term(t -> t.field("chapterId").value(chapterId)));
                }
                return b;
            }));

            SearchResponse<JsonNode> response = elasticsearchClient.search(s -> s
                            .index("courseware_index")
                            .query(query)
                            .from((page - 1) * size)
                            .size(size),
                    JsonNode.class
            );

            long total = response.hits().total().value();
            List<CoursewareEntity> list = response.hits().hits().stream()
                    .map(hit -> {
                        JsonNode source = hit.source();
                        CoursewareEntity entity = new CoursewareEntity();
                        entity.setId(hit.id());
                        entity.setCourseId(source.has("courseId") && !source.get("courseId").isNull()
                                ? source.get("courseId").asInt() : null);
                        entity.setCourseName(source.has("courseName") ? source.get("courseName").asText() : null);
                        entity.setChapterId(source.has("chapterId") && !source.get("chapterId").isNull()
                                ? source.get("chapterId").asLong() : null);
                        entity.setTitle(source.has("title") ? source.get("title").asText() : null);
                        entity.setFileType(source.has("fileType") ? source.get("fileType").asText() : null);
                        entity.setFileSize(source.has("fileSize") && !source.get("fileSize").isNull()
                                ? source.get("fileSize").asLong() : null);
                        entity.setTotalPages(source.has("totalPages") && !source.get("totalPages").isNull()
                                ? source.get("totalPages").asInt() : null);
                        entity.setProcessStatus(source.has("processStatus") ? source.get("processStatus").asText() : null);
                        entity.setCreaterId(source.has("createrId") && !source.get("createrId").isNull()
                                ? source.get("createrId").asInt() : null);
                        entity.setCreaterName(source.has("createrName") ? source.get("createrName").asText() : null);
                        entity.setCreateTime(source.has("createTime") ? source.get("createTime").asText() : null);
                        entity.setUpdateTime(source.has("updateTime") ? source.get("updateTime").asText() : null);
                        return entity;
                    })
                    .toList();

            return PageResult.of(list, total, page, size);
        } catch (Exception e) {
            throw new RuntimeException("查询课件列表失败", e);
        }
    }

    // ==================== 课件切片管理 ====================

    /** 为课件添加一个页面切片（每页一个切片） */
    public String addChunk(String coursewareId, Integer courseId, String coursewareTitle,
                           int pageNumber, String content) {
        String chunkId = UUID.randomUUID().toString().replace("-", "");

        float[] vector = embeddingModel.embed(content);
        List<Float> embedding = new ArrayList<>(vector.length);
        for (float v : vector) embedding.add(v);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId", chunkId);
        metadata.put("coursewareId", coursewareId);
        metadata.put("courseId", courseId);
        metadata.put("coursewareTitle", coursewareTitle);
        metadata.put("pageNumber", pageNumber);

        Map<String, Object> doc = new HashMap<>();
        doc.put("embedding", embedding);
        doc.put("content", content);
        doc.put("metadata", metadata);

        try {
            elasticsearchClient.index(i -> i
                    .index("courseware_chunk_vector")
                    .id(chunkId)
                    .document(doc)
            );
        } catch (Exception e) {
            throw new RuntimeException("添加课件切片失败", e);
        }
        return chunkId;
    }

    /** 获取课件切片数量 */
    public long countChunks(String coursewareId) {
        try {
            return elasticsearchClient.count(c -> c
                    .index("courseware_chunk_vector")
                    .query(q -> q.term(t -> t.field("metadata.coursewareId.keyword").value(coursewareId)))
            ).count();
        } catch (Exception e) {
            log.warn("统计课件切片数量失败: {}", e.getMessage());
            return 0;
        }
    }

    // ==================== 课件语义搜索 ====================

    /** 语义搜索课件内容 */
    public List<SearchResult> search(SearchRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : 10;
        String queryText = request.getQuery();

        // 检查索引是否存在
        boolean indexExists;
        try {
            indexExists = elasticsearchClient.indices().exists(e -> e.index("courseware_chunk_vector")).value();
        } catch (Exception ex) {
            log.warn("检查 courseware_chunk_vector 是否存在时出错，假定不存在: {}", ex.getMessage());
            indexExists = false;
        }

        if (!indexExists) {
            log.warn("courseware_chunk_vector 不存在，返回空列表");
            return List.of();
        }

        float[] queryVector = embeddingModel.embed(queryText);
        List<Float> queryVectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) queryVectorList.add(v);

        try {
            var searchBuilder = elasticsearchClient.search(s -> {
                KnnSearch.Builder knnBuilder = new KnnSearch.Builder()
                        .field("embedding")
                        .queryVector(queryVectorList)
                        .k(topK)
                        .numCandidates(topK * 10);

                // 按 courseId 过滤
                if (request.getTextbookId() != null && !request.getTextbookId().isBlank()) {
                    // 复用 textbookId 字段传 courseId
                    knnBuilder.filter(Query.of(fq -> fq
                            .term(t -> t.field("metadata.courseId").value(Integer.parseInt(request.getTextbookId())))
                    ));
                }

                return s.index("courseware_chunk_vector")
                        .knn(knnBuilder.build());
            }, JsonNode.class);

            return searchBuilder.hits().hits().stream()
                    .map(hit -> {
                        JsonNode source = hit.source();
                        JsonNode metadata = source.get("metadata");
                        int pageNumber = metadata.has("pageNumber") ? metadata.get("pageNumber").asInt() : 0;
                        return SearchResult.builder()
                                .chunkId(metadata.get("chunkId").asText())
                                .textbookId(metadata.has("coursewareId") ? metadata.get("coursewareId").asText() : "")
                                .textbookTitle(metadata.has("coursewareTitle") ? metadata.get("coursewareTitle").asText() : "")
                                .chunkIndex(pageNumber)
                                .startPage(pageNumber)
                                .endPage(pageNumber)
                                .paragraphIndex(0)
                                .content(source.has("content") && source.get("content") != null
                                        ? source.get("content").asText() : "")
                                .score(hit.score() != null ? hit.score() : 0.0)
                                .build();
                    })
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("课件语义搜索失败", e);
        }
    }
}