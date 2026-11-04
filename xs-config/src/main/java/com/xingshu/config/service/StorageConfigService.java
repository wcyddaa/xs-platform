package com.xingshu.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingshu.config.entity.StorageConfig;
import com.xingshu.config.mapper.StorageConfigMapper;
import com.xingshu.config.security.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 对象存储配置服务：管理员维护、加密存储、连通性测试、内部服务下发。
 */
@Service
public class StorageConfigService {

    private static final Logger log = LoggerFactory.getLogger(StorageConfigService.class);

    private static final String AGC_TOKEN_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/token";

    private final StorageConfigMapper mapper;
    private final SecretCipher cipher;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public StorageConfigService(StorageConfigMapper mapper, SecretCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    public List<StorageConfig> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<StorageConfig>()
                .orderByDesc(StorageConfig::getEnabled)
                .orderByAsc(StorageConfig::getId));
    }

    /** 当前启用的存储配置（内部服务使用，含解密后的密钥） */
    public StorageConfig active() {
        List<StorageConfig> list = mapper.selectList(new LambdaQueryWrapper<StorageConfig>()
                .eq(StorageConfig::getEnabled, 1)
                .orderByDesc(StorageConfig::getId));
        if (list.isEmpty()) {
            return null;
        }
        StorageConfig config = list.get(0);
        config.setClientSecret(cipher.decrypt(config.getClientSecret()));
        return config;
    }

    /** 保存配置：密钥留空表示沿用原值；密文入库 */
    public StorageConfig save(StorageConfig input, Long operatorId) {
        StorageConfig target;
        if (input.getId() != null) {
            target = mapper.selectById(input.getId());
            if (target == null) {
                throw new IllegalArgumentException("存储配置不存在");
            }
        } else {
            target = new StorageConfig();
            target.setEnabled(1);
        }
        target.setProvider(input.getProvider() == null || input.getProvider().isBlank() ? "agc" : input.getProvider());
        target.setBucket(input.getBucket());
        target.setRegion(input.getRegion());
        target.setClientId(input.getClientId());
        if (input.getClientSecret() != null && !input.getClientSecret().isBlank()) {
            target.setClientSecret(cipher.encrypt(input.getClientSecret().trim()));
            target.setStatus("untested");
            target.setTestMessage(null);
        }
        if (input.getEnabled() != null) {
            target.setEnabled(input.getEnabled());
        }
        target.setUpdaterId(operatorId);
        if (target.getId() == null) {
            target.setCreaterId(operatorId);
            mapper.insert(target);
        } else {
            mapper.updateById(target);
        }
        // 同一时间只保留一个启用项
        if (Integer.valueOf(1).equals(target.getEnabled())) {
            mapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<StorageConfig>()
                    .ne(StorageConfig::getId, target.getId())
                    .set(StorageConfig::getEnabled, 0));
        }
        return target;
    }

    /** 连通性测试：用 client_id/secret 向 AGC 换取访问令牌 */
    public StorageConfig test(StorageConfig input, Long operatorId) {
        StorageConfig stored = input.getId() == null ? null : mapper.selectById(input.getId());
        String clientId = (input.getClientId() != null && !input.getClientId().isBlank())
                ? input.getClientId() : (stored != null ? stored.getClientId() : null);
        String secret;
        if (input.getClientSecret() != null && !input.getClientSecret().isBlank()) {
            secret = input.getClientSecret().trim();
        } else {
            secret = stored == null ? null : cipher.decrypt(stored.getClientSecret());
        }
        if (clientId == null || secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("请先填写 client_id 与 client_secret");
        }

        String message;
        boolean success;
        try {
            String body = "grant_type=client_credentials"
                    + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(secret, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(AGC_TOKEN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            String token = node.path("access_token").asText(null);
            if (response.statusCode() == 200 && token != null && !token.isBlank()) {
                success = true;
                message = "凭据有效，已成功换取 AGC 访问令牌";
            } else {
                success = false;
                message = "凭据校验失败: HTTP " + response.statusCode() + " "
                        + node.path("error_description").asText(node.path("error").asText(""));
            }
        } catch (Exception e) {
            success = false;
            message = "连接 AGC 失败: " + e.getMessage();
            log.warn("AGC 连通性测试异常: {}", e.getMessage());
        }

        StorageConfig target = stored != null ? stored : new StorageConfig();
        target.setStatus(success ? "success" : "fail");
        target.setTestMessage(message.length() > 400 ? message.substring(0, 400) : message);
        target.setLastTestTime(LocalDateTime.now());
        target.setUpdaterId(operatorId);
        if (stored == null) {
            target.setProvider(input.getProvider() == null ? "agc" : input.getProvider());
            target.setBucket(input.getBucket());
            target.setRegion(input.getRegion());
            target.setClientId(clientId);
            target.setClientSecret(cipher.encrypt(secret));
            target.setEnabled(input.getEnabled() == null ? 1 : input.getEnabled());
            target.setCreaterId(operatorId);
            mapper.insert(target);
        } else {
            mapper.updateById(target);
        }
        return target;
    }

    /** 对外展示前脱敏：不下发密钥，只给出是否已配置 */
    public StorageConfig desensitize(StorageConfig config) {
        if (config == null) {
            return null;
        }
        config.setClientSecret(null);
        return config;
    }
}
