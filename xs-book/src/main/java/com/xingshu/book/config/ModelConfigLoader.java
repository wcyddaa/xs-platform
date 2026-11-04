package com.xingshu.book.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 模型配置加载器 - 从 xs-config 配置服务获取嵌入向量模型配置
 */
@Configuration
public class ModelConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigLoader.class);

    private static final String XS_CONFIG_URL = "http://localhost:8086";

    /** 默认配置（配置服务不可用时的回退） */
    @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.embedding.options.model:BAAI/bge-m3}")
    private String defaultModel;

    /** 内部服务令牌，用于调用 xs-config 的内部配置接口 */
    @Value("${xingshu.internal-token:xingshu-internal-2026}")
    private String internalToken;

    private String baseUrl;
    private String apiKey;
    private String modelName;

    @PostConstruct
    public void loadConfig() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = XS_CONFIG_URL + "/api/model-config/agent-config?configType=EMBEDDING&agentName=default";
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Token", internalToken);
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> responseEntity = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> response = responseEntity.getBody();

            if (response != null && Integer.valueOf(200).equals(response.get("code"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null) {
                    this.baseUrl = (String) data.getOrDefault("baseUrl", defaultBaseUrl);
                    this.apiKey = (String) data.getOrDefault("apiKey", defaultApiKey);
                    this.modelName = (String) data.getOrDefault("modelName", defaultModel);
                    log.info("从 xs-config 加载嵌入模型配置: model={}, baseUrl={}", modelName, baseUrl);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("无法从 xs-config 加载嵌入模型配置，使用默认配置: {}", e.getMessage());
        }

        // 回退到 application.yaml 默认配置
        this.baseUrl = defaultBaseUrl;
        this.apiKey = defaultApiKey;
        this.modelName = defaultModel;
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(modelName)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}