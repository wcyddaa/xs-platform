package com.xingshu.book.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 嵌入向量模型配置加载器。
 *
 * <p>密钥唯一来源是 xs-config 的受保护内部接口（需 X-Internal-Token），
 * 不再读取 application.yaml / 环境变量中的 api-key，也不在日志中输出密钥。</p>
 *
 * <p>配置缺失时服务仍可启动，但向量检索会抛出明确提示；管理员在前端
 * 「模型配置」中补齐 EMBEDDING 配置后，下一次调用会自动重新加载，无需重启。</p>
 */
@Configuration
public class ModelConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigLoader.class);

    private static final String PLACEHOLDER_KEY = "not-configured";
    private static final long REFRESH_MILLIS = 300_000L;
    private static final long FAIL_RETRY_MILLIS = 10_000L;

    @Value("${spring.ai.openai.base-url:https://api.siliconflow.cn}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.embedding.options.model:BAAI/bge-m3}")
    private String defaultModel;

    @Value("${xingshu.internal-token:xingshu-internal-2026}")
    private String internalToken;

    @Value("${xingshu.xs-config-url:http://localhost:8086}")
    private String xsConfigUrl;

    private final RestTemplate restTemplate;

    private final Object lock = new Object();

    private volatile RuntimeConfig runtime;
    private volatile Instant lastAttempt = Instant.EPOCH;
    private volatile boolean lastAttemptFailed;
    private volatile OpenAiEmbeddingModel delegate;
    private volatile String delegateSignature;

    public ModelConfigLoader() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostConstruct
    public void init() {
        refresh(true);
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return currentDelegate().call(request);
            }

            @Override
            public float[] embed(Document document) {
                return currentDelegate().embed(document);
            }
        };
    }

    private EmbeddingModel currentDelegate() {
        RuntimeConfig config = currentRuntime();
        if (PLACEHOLDER_KEY.equals(config.apiKey())) {
            throw new IllegalStateException("嵌入模型尚未配置，请在管理员端「模型配置」中填写 EMBEDDING 模型并发布");
        }

        String signature = signature(config);
        OpenAiEmbeddingModel model = delegate;
        if (model == null || !signature.equals(delegateSignature)) {
            synchronized (lock) {
                config = currentRuntime();
                if (PLACEHOLDER_KEY.equals(config.apiKey())) {
                    throw new IllegalStateException("嵌入模型尚未配置，请在管理员端「模型配置」中填写 EMBEDDING 模型并发布");
                }
                if (delegate == null || !signature(config).equals(delegateSignature)) {
                    OpenAiApi openAiApi = OpenAiApi.builder()
                            .baseUrl(config.baseUrl())
                            .apiKey(config.apiKey())
                            .build();
                    OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                            .model(config.modelName())
                            .build();
                    delegate = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
                    delegateSignature = signature(config);
                }
                model = delegate;
            }
        }
        return model;
    }

    private RuntimeConfig currentRuntime() {
        RuntimeConfig current = runtime;
        boolean needRefresh = current == null
                || PLACEHOLDER_KEY.equals(current.apiKey())
                || Duration.between(lastAttempt, Instant.now()).toMillis() >= REFRESH_MILLIS;
        if (needRefresh) {
            refresh(false);
            current = runtime;
        }
        if (current == null) {
            current = new RuntimeConfig(defaultBaseUrl, PLACEHOLDER_KEY, defaultModel);
            runtime = current;
        }
        return current;
    }

    private void refresh(boolean startup) {
        synchronized (lock) {
            Instant now = Instant.now();
            if (!startup && lastAttemptFailed
                    && Duration.between(lastAttempt, now).toMillis() < FAIL_RETRY_MILLIS) {
                return;
            }
            lastAttempt = now;
            try {
                String url = xsConfigUrl
                        + "/api/model-config/agent-config?configType=EMBEDDING&agentName=default";
                HttpHeaders headers = new HttpHeaders();
                headers.set("X-Internal-Token", internalToken);
                @SuppressWarnings("rawtypes")
                ResponseEntity<Map> response = restTemplate.exchange(
                        url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
                Map<String, Object> body = response.getBody();
                if (body != null && Integer.valueOf(200).equals(body.get("code"))) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    String apiKey = data == null ? null : (String) data.get("apiKey");
                    if (apiKey != null && !apiKey.isBlank()) {
                        String baseUrl = stringValue(data, "baseUrl", defaultBaseUrl);
                        String modelName = stringValue(data, "modelName", defaultModel);
                        runtime = new RuntimeConfig(baseUrl, apiKey, modelName);
                        lastAttemptFailed = false;
                        log.info("从 xs-config 加载嵌入模型配置: model={}, baseUrl={}", modelName, baseUrl);
                        return;
                    }
                    log.warn("xs-config 中 EMBEDDING 配置缺少 apiKey，请管理员在「模型配置」中填写并发布");
                } else {
                    log.warn("xs-config 未返回可用的 EMBEDDING 配置");
                }
            } catch (Exception e) {
                log.warn("从 xs-config 获取嵌入模型配置失败: {}", e.getMessage());
            }
            lastAttemptFailed = true;
            if (runtime == null) {
                runtime = new RuntimeConfig(defaultBaseUrl, PLACEHOLDER_KEY, defaultModel);
            }
        }
    }

    private static String stringValue(Map<String, Object> data, String key, String fallback) {
        Object value = data == null ? null : data.get(key);
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private static String signature(RuntimeConfig config) {
        return config.baseUrl() + "|" + config.modelName() + "|" + config.apiKey();
    }

    private record RuntimeConfig(String baseUrl, String apiKey, String modelName) {
    }
}
