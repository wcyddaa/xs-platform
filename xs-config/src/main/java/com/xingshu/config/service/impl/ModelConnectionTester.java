package com.xingshu.config.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 模型连通性测试：仅由服务端使用已保存的地址与密钥发起真实调用。
 */
@Component
public class ModelConnectionTester {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public record Result(boolean success, long latencyMs, String message, String reply) {
    }

    public Result test(String baseUrl, String apiKey, String modelName) {
        if (baseUrl == null || baseUrl.isBlank() || apiKey == null || apiKey.isBlank()
                || modelName == null || modelName.isBlank()) {
            return new Result(false, 0, "模型 API 地址、密钥或模型名称未配置", "");
        }
        String endpoint = baseUrl.endsWith("/chat/completions")
                ? baseUrl
                : baseUrl.replaceAll("/+$", "") + "/chat/completions";
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "model", modelName,
                    "messages", List.of(Map.of("role", "user", "content", "请只回复：连接成功")),
                    "max_tokens", 24,
                    "temperature", 0
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            long start = System.currentTimeMillis();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new Result(false, latency,
                        "连接失败：HTTP " + response.statusCode() + " " + snippet(response.body()), "");
            }
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            String reply = root.path("choices").path(0).path("message").path("content").asText("");
            if (reply.isBlank()) {
                reply = root.path("choices").path(0).path("text").asText("");
            }
            return new Result(true, latency, "连接成功",
                    snippet(reply.isBlank() ? "模型已响应" : reply));
        } catch (java.net.http.HttpTimeoutException e) {
            return new Result(false, 0, "连接超时，请检查 API 地址或网络", "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, 0, "测试被中断，请重试", "");
        } catch (IllegalArgumentException e) {
            return new Result(false, 0, "API 地址格式不正确：" + e.getMessage(), "");
        } catch (Exception e) {
            return new Result(false, 0,
                    "连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), "");
        }
    }

    private String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() > 160 ? compact.substring(0, 160) + "..." : compact;
    }
}
