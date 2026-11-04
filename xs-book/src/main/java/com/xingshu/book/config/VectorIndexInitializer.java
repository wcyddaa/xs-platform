package com.xingshu.book.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VectorIndexInitializer {

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Value("${spring.ai.vectorstore.elasticsearch.index-name:textbook_chunk_vector}")
    private String vectorIndexName;

    @Value("${spring.ai.vectorstore.elasticsearch.dimensions:1024}")
    private int dimensions;

    @Value("${spring.ai.vectorstore.elasticsearch.similarity:cosine}")
    private String similarity;

    @PostConstruct
    public void createVectorIndexIfNotExists() {
        try {
            // 1. 创建教材元数据索引
            boolean textbookExists = indexExists("textbook_index");
            if (!textbookExists) {
                elasticsearchClient.indices().create(CreateIndexRequest.of(r -> r
                        .index("textbook_index")
                        .mappings(TypeMapping.of(m -> m
                                .properties("title", Property.of(p -> p
                                        .text(t -> t.analyzer("standard"))
                                ))
                                .properties("isbn", Property.of(p -> p.keyword(k -> k)))
                                .properties("author", Property.of(p -> p.keyword(k -> k)))
                                .properties("category", Property.of(p -> p
                                        .text(t -> t.analyzer("standard"))
                                ))
                                .properties("totalPages", Property.of(p -> p.integer(i -> i)))
                                .properties("publishDate", Property.of(p -> p.keyword(k -> k)))
                                .properties("fileType", Property.of(p -> p.keyword(k -> k)))
                                .properties("fileSize", Property.of(p -> p.long_(l -> l)))
                                .properties("createTime", Property.of(p -> p.keyword(k -> k)))
                                .properties("updateTime", Property.of(p -> p.keyword(k -> k)))
                        ))
                ));
                System.out.println("Created textbook index: textbook_index");
            }

            // 2. 创建向量索引
            boolean exists = indexExists(vectorIndexName);
            if (!exists) {
                elasticsearchClient.indices().create(CreateIndexRequest.of(r -> r
                        .index(vectorIndexName)
                        .mappings(TypeMapping.of(m -> m
                                .properties("embedding", Property.of(p -> p
                                        .denseVector(DenseVectorProperty.of(d -> d
                                                .dims(dimensions)
                                                .similarity(convertSimilarity(similarity))
                                        ))
                                ))
                                .properties("content", Property.of(p -> p
                                        .text(TextProperty.of(t -> t))
                                ))
                                .properties("metadata", Property.of(p -> p
                                        .object(o -> o))
                                )
                        ))
                ));
                System.out.println("Created vector index: " + vectorIndexName);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create index", e);
        }
    }

    /**
     * 检查索引是否存在。ES 9.x 的 indices.exists 在某些版本可能返回空 body，
     * 这里用 try-catch 兜底，异常时视为不存在。
     */
    private boolean indexExists(String indexName) {
        try {
            return elasticsearchClient.indices().exists(ExistsRequest.of(r -> r.index(indexName))).value();
        } catch (Exception e) {
            System.out.println("Failed to check index existence, assuming not exists: " + e.getMessage());
            return false;
        }
    }

    private DenseVectorSimilarity convertSimilarity(String sim) {
        return switch (sim.toLowerCase()) {
            case "l2_norm" -> DenseVectorSimilarity.L2Norm;
            case "dot_product" -> DenseVectorSimilarity.DotProduct;
            default -> DenseVectorSimilarity.Cosine;
        };
    }
}