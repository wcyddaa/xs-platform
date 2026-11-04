package com.xingshu.book.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.xingshu.common.base.PageResult;
import com.xingshu.book.model.dto.ChunkListResult;
import com.xingshu.book.model.dto.SearchRequest;
import com.xingshu.book.model.dto.SearchResult;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VectorSearchService {

    private static final int CONTENT_PREVIEW_MAX = 200;

    private final EmbeddingModel embeddingModel;
    private final ElasticsearchClient elasticsearchClient;

    public VectorSearchService(EmbeddingModel embeddingModel,
                               ElasticsearchClient elasticsearchClient) {
        this.embeddingModel = embeddingModel;
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 为指定教材添加一个切片（生成向量 + 写入 ES）
     */
    public String addChunk(String textbookId, String textbookTitle,
                           int chunkIndex, int startPage, int endPage,
                           int paragraphIndex, String content) {
        String chunkId = UUID.randomUUID().toString().replace("-", "");

        float[] vector = embeddingModel.embed(content);
        List<Float> embedding = new ArrayList<>(vector.length);
        for (float v : vector) {
            embedding.add(v);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("chunkId", chunkId);
        metadata.put("textbookId", textbookId);
        metadata.put("textbookTitle", textbookTitle);
        metadata.put("chunkIndex", chunkIndex);
        metadata.put("startPage", startPage);
        metadata.put("endPage", endPage);
        metadata.put("paragraphIndex", paragraphIndex);

        Map<String, Object> doc = new HashMap<>();
        doc.put("embedding", embedding);
        doc.put("content", content);
        doc.put("metadata", metadata);

        try {
            elasticsearchClient.index(i -> i
                    .index("textbook_chunk_vector")
                    .id(chunkId)
                    .document(doc)
            );
        } catch (Exception e) {
            throw new RuntimeException("添加切片失败", e);
        }
        return chunkId;
    }

    /**
     * 批量添加切片
     */
    public void addChunks(List<Document> documents) {
        for (Document doc : documents) {
            String content = doc.getText() != null ? doc.getText() : "";
            float[] vector = embeddingModel.embed(content);
            List<Float> embedding = new ArrayList<>(vector.length);
            for (float v : vector) {
                embedding.add(v);
            }

            Map<String, Object> esDoc = new HashMap<>();
            esDoc.put("embedding", embedding);
            esDoc.put("content", content);
            esDoc.put("metadata", doc.getMetadata());

            try {
                elasticsearchClient.index(i -> i
                        .index("textbook_chunk_vector")
                        .id(doc.getId())
                        .document(esDoc)
                );
            } catch (Exception e) {
                throw new RuntimeException("批量添加切片失败", e);
            }
        }
    }

    /**
     * 删除指定教材的所有切片
     */
    public void deleteByTextbookId(String textbookId) {
        try {
            var response = elasticsearchClient.search(s -> s
                            .index("textbook_chunk_vector")
                            .query(q -> q
                                    .term(t -> t.field("metadata.textbookId.keyword").value(textbookId))
                            )
                            .size(10000)
                            .source(sc -> sc.fetch(false)),
                    JsonNode.class
            );

            List<String> ids = response.hits().hits().stream()
                    .map(hit -> hit.id())
                    .toList();

            if (!ids.isEmpty()) {
                for (String id : ids) {
                    elasticsearchClient.delete(d -> d
                            .index("textbook_chunk_vector")
                            .id(id)
                    );
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("删除教材切片失败", e);
        }
    }

    /**
     * 强制清空所有切片（用于清除残留数据，无视 textbookId）
     */
    public long forceDeleteAllChunks() {
        try {
            var response = elasticsearchClient.deleteByQuery(d -> d
                    .index("textbook_chunk_vector")
                    .query(q -> q.matchAll(m -> m))
                    .refresh(true)
                    .conflicts(Conflicts.Proceed)
            );
            return response.deleted();
        } catch (Exception e) {
            throw new RuntimeException("强制清空切片失败", e);
        }
    }

    /**
     * 删除指定的单个切片
     */
    public void deleteChunk(String chunkId) {
        try {
            elasticsearchClient.delete(d -> d
                    .index("textbook_chunk_vector")
                    .id(chunkId)
            );
        } catch (Exception e) {
            throw new RuntimeException("删除切片失败", e);
        }
    }

    /**
     * 分页查询指定教材的切片列表
     */
    public PageResult<ChunkListResult> listChunks(String textbookId, String keyword,
                                                   int page, int size) {
        try {
            List<Query> mustQueries = new ArrayList<>();
            mustQueries.add(Query.of(q -> q
                    .term(t -> t.field("metadata.textbookId.keyword").value(textbookId))
            ));

            if (keyword != null && !keyword.isBlank()) {
                mustQueries.add(Query.of(q -> q
                        .match(m -> m.field("content").query(keyword))
                ));
            }

            Query finalQuery = Query.of(q -> q.bool(b -> b.must(mustQueries)));

            SearchResponse<JsonNode> response = elasticsearchClient.search(s -> s
                            .index("textbook_chunk_vector")
                            .query(finalQuery)
                            .from((page - 1) * size)
                            .size(size),
                    JsonNode.class
            );

            long total = response.hits().total().value();
            List<ChunkListResult> list = response.hits().hits().stream()
                    .map(hit -> {
                        JsonNode source = hit.source();
                        JsonNode metadata = source.get("metadata");
                        return ChunkListResult.builder()
                                .chunkId(metadata.get("chunkId").asText())
                                .textbookId(metadata.get("textbookId").asText())
                                .textbookTitle(metadata.get("textbookTitle").asText())
                                .chunkIndex(metadata.get("chunkIndex").asInt())
                                .startPage(metadata.get("startPage").asInt())
                                .endPage(metadata.get("endPage").asInt())
                                .paragraphIndex(metadata.has("paragraphIndex") && !metadata.get("paragraphIndex").isNull()
                                        ? metadata.get("paragraphIndex").asInt() : null)
                                .content(truncateContent(
                                        source.has("content") && source.get("content") != null
                                                ? source.get("content").asText() : ""))
                                .build();
                    })
                    .toList();

            return PageResult.of(list, total, page, size);

        } catch (Exception e) {
            throw new RuntimeException("查询切片列表失败", e);
        }
    }

    /**
     * 语义搜索：先生成查询向量，再执行 kNN 搜索
     */
    public List<SearchResult> search(SearchRequest request) {
        int topK = request.getTopK() != null ? request.getTopK() : 10;
        String queryText = request.getQuery();

        // 生成查询向量
        float[] queryVector = embeddingModel.embed(queryText);
        List<Float> queryVectorList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) {
            queryVectorList.add(v);
        }

        try {
            // 构建 kNN 搜索
            var searchBuilder = elasticsearchClient.search(s -> s
                    .index("textbook_chunk_vector")
                    .knn(k -> {
                        KnnSearch.Builder knnBuilder = k
                                .field("embedding")
                                .queryVector(queryVectorList)
                                .k(topK)
                                .numCandidates(topK * 10);

                        // 如果有 textbookId 过滤条件
                        if (request.getTextbookId() != null && !request.getTextbookId().isBlank()) {
                            knnBuilder.filter(Query.of(fq -> fq
                                    .term(t -> t.field("metadata.textbookId.keyword").value(request.getTextbookId()))
                            ));
                        }

                        // 相似度阈值
                        Double minScore = request.getMinScore();
                        if (minScore != null && minScore > 0) {
                            knnBuilder.similarity(minScore.floatValue());
                        }

                        return knnBuilder;
                    }),
                    JsonNode.class
            );

            SearchResponse<JsonNode> response = searchBuilder;

            return response.hits().hits().stream()
                    .map(hit -> {
                        JsonNode source = hit.source();
                        JsonNode metadata = source.get("metadata");
                        int startPage = metadata.get("startPage").asInt();
                        int endPage = metadata.get("endPage").asInt();
                        return SearchResult.builder()
                                .chunkId(metadata.get("chunkId").asText())
                                .textbookId(metadata.get("textbookId").asText())
                                .textbookTitle(metadata.get("textbookTitle").asText())
                                .chunkIndex(metadata.get("chunkIndex").asInt())
                                .startPage(startPage)
                                .endPage(endPage)
                                .paragraphIndex(metadata.has("paragraphIndex") && !metadata.get("paragraphIndex").isNull()
                                        ? metadata.get("paragraphIndex").asInt() : null)
                                .content(source.has("content") && source.get("content") != null
                                        ? source.get("content").asText() : "")
                                .score(hit.score() != null ? hit.score() : 0.0)
                                .build();
                    })
                    // 在 Java 侧按页数范围过滤（避免 ES RangeQuery 构建 API 兼容性问题）
                    .filter(r -> {
                        if (request.getStartPage() != null && r.getEndPage() < request.getStartPage()) {
                            return false;
                        }
                        if (request.getEndPage() != null && r.getStartPage() > request.getEndPage()) {
                            return false;
                        }
                        return true;
                    })
                    .toList();

        } catch (Exception e) {
            throw new RuntimeException("语义搜索失败", e);
        }
    }

    // ==================== 辅助方法 ====================

    private String truncateContent(String content) {
        if (content == null) return "";
        return content.length() > CONTENT_PREVIEW_MAX
                ? content.substring(0, CONTENT_PREVIEW_MAX) + "..."
                : content;
    }
}