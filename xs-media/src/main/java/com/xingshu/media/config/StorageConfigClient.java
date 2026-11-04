package com.xingshu.media.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 从 xs-config 读取管理员维护的存储配置（内部接口，带内部令牌）。
 * <p>
 * 结果缓存 30 秒：切换 provider 后无需重启业务服务，最多 30 秒自动生效。
 * 未配置时返回 null，调用方可回退到 application.yaml 中的本地配置。
 */
public class StorageConfigClient {

    private static final Logger log = LoggerFactory.getLogger(StorageConfigClient.class);

    private static final long CACHE_MILLIS = 30_000L;

    private final String baseUrl;
    private final String internalToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private volatile Snapshot cached;
    private volatile long cachedAt;

    public StorageConfigClient(String baseUrl, String internalToken) {
        this.baseUrl = baseUrl;
        this.internalToken = internalToken;
    }

    /** 当前启用的存储配置；未配置或读取失败返回 null */
    public Snapshot fetch() {
        long now = System.currentTimeMillis();
        long at = cachedAt;
        if (at > 0 && now - at < CACHE_MILLIS) {
            return cached;
        }
        synchronized (this) {
            if (cachedAt > 0 && System.currentTimeMillis() - cachedAt < CACHE_MILLIS) {
                return cached;
            }
            Snapshot loaded = load();
            cached = loaded;
            cachedAt = System.currentTimeMillis();
            return loaded;
        }
    }

    private Snapshot load() {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/storage-config/internal"))
                    .header("X-Internal-Token", internalToken)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("xs-config 存储配置不可用: HTTP {}", response.statusCode());
                return null;
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            String provider = data.path("provider").asText(null);
            if (provider == null || provider.isBlank()) {
                return null;
            }
            return new Snapshot(provider.trim().toLowerCase(),
                    data.path("endpoint").asText(null),
                    data.path("region").asText(null),
                    data.path("bucket").asText(null),
                    data.path("clientId").asText(null),
                    data.path("clientSecret").asText(null));
        } catch (Exception e) {
            log.debug("读取 xs-config 存储配置失败: {}", e.getMessage());
            return null;
        }
    }

    /** 一次配置快照；signature 用于判断配置是否变化 */
    public record Snapshot(String provider, String endpoint, String region, String bucket,
                           String clientId, String clientSecret) {

        public String signature() {
            return provider + "|" + endpoint + "|" + region + "|" + bucket + "|" + clientId
                    + "|" + (clientSecret == null ? "0" : Integer.toHexString(clientSecret.hashCode()));
        }
    }
}
