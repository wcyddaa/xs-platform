package com.xingshu.config.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import com.xingshu.config.entity.StorageConfig;
import com.xingshu.config.mapper.StorageConfigMapper;
import com.xingshu.config.security.SecretCipher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 对象存储配置服务：管理员维护、加密存储、连通性测试、内部服务下发。
 */
@Service
public class StorageConfigService {

    private static final Logger log = LoggerFactory.getLogger(StorageConfigService.class);

    private static final String AGC_TOKEN_URL = "https://oauth-login.cloud.huawei.com/oauth2/v3/token";

    /** 支持的存储类型：华为 AGC / 阿里云 OSS / 腾讯云 COS / 仅本地 */
    private static final Set<String> PROVIDERS = Set.of("agc", "oss", "cos", "local");

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
        String provider = normalizeProvider(input.getProvider() != null && !input.getProvider().isBlank()
                ? input.getProvider() : target.getProvider());
        boolean connectionChanged = !provider.equals(target.getProvider())
                || !str(target.getEndpoint()).equals(str(trimToNull(input.getEndpoint())))
                || !str(target.getBucket()).equals(str(trimToNull(input.getBucket())))
                || !str(target.getRegion()).equals(str(trimToNull(input.getRegion())))
                || !str(target.getClientId()).equals(str(trimToNull(input.getClientId())));
        target.setProvider(provider);
        target.setEndpoint(trimToNull(input.getEndpoint()));
        target.setBucket(trimToNull(input.getBucket()));
        target.setRegion(trimToNull(input.getRegion()));
        target.setClientId(trimToNull(input.getClientId()));
        if (input.getClientSecret() != null && !input.getClientSecret().isBlank()) {
            target.setClientSecret(cipher.encrypt(input.getClientSecret().trim()));
            connectionChanged = true;
        }
        if (connectionChanged) {
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

    /** 连通性测试：按 provider 选择对应的校验方式 */
    public StorageConfig test(StorageConfig input, Long operatorId) {
        StorageConfig stored = input.getId() == null ? null : mapper.selectById(input.getId());
        String provider = normalizeProvider(str(input.getProvider()).isBlank()
                ? (stored != null ? stored.getProvider() : null) : input.getProvider());
        String clientId = firstNonBlank(input.getClientId(), stored != null ? stored.getClientId() : null);
        String secret = firstNonBlank(input.getClientSecret(),
                stored != null ? cipher.decrypt(stored.getClientSecret()) : null);
        String endpoint = firstNonBlank(input.getEndpoint(), stored != null ? stored.getEndpoint() : null);
        String region = firstNonBlank(input.getRegion(), stored != null ? stored.getRegion() : null);
        String bucket = firstNonBlank(input.getBucket(), stored != null ? stored.getBucket() : null);

        TestResult result = switch (provider) {
            case "local" -> testLocal();
            case "oss" -> testOss(endpoint, clientId, secret, bucket);
            case "cos" -> testCos(region, clientId, secret, bucket);
            default -> testAgc(clientId, secret);
        };

        StorageConfig target = stored != null ? stored : new StorageConfig();
        target.setStatus(result.success() ? "success" : "fail");
        target.setTestMessage(result.message().length() > 400
                ? result.message().substring(0, 400) : result.message());
        target.setLastTestTime(LocalDateTime.now());
        target.setUpdaterId(operatorId);
        target.setProvider(provider);
        target.setEndpoint(endpoint);
        target.setBucket(bucket);
        target.setRegion(region);
        target.setClientId(clientId);
        if (stored == null) {
            // 首次测试只返回结果，不落库：避免点一次测试就产生一条启用记录
            target.setEnabled(input.getEnabled());
        } else {
            mapper.updateById(target);
        }
        return target;
    }

    private TestResult testAgc(String clientId, String secret) {
        if (clientId == null || secret == null || secret.isBlank()) {
            return new TestResult(false, "华为 AGC 需要填写 Client ID 与 Client Secret");
        }
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
                return new TestResult(true, "凭据有效，已成功换取 AGC 访问令牌");
            }
            return new TestResult(false, "凭据校验失败: HTTP " + response.statusCode() + " "
                    + node.path("error_description").asText(node.path("error").asText("")));
        } catch (Exception e) {
            log.warn("AGC 连通性测试异常: {}", e.getMessage());
            return new TestResult(false, "连接 AGC 失败: " + e.getMessage());
        }
    }

    private TestResult testOss(String endpoint, String accessKeyId, String accessKeySecret, String bucket) {
        if (endpoint == null || accessKeyId == null || accessKeySecret == null || bucket == null) {
            return new TestResult(false, "阿里云 OSS 需要填写 Endpoint、Bucket、AccessKeyId、AccessKeySecret");
        }
        OSS oss = null;
        try {
            oss = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            oss.getBucketInfo(bucket);
            return new TestResult(true, "连接成功，存储桶 " + bucket + " 可访问");
        } catch (OSSException e) {
            log.warn("OSS 连通性测试异常: {}", e.getMessage());
            return new TestResult(false, "OSS 校验失败: " + e.getErrorCode() + " " + e.getErrorMessage());
        } catch (Exception e) {
            log.warn("OSS 连通性测试异常: {}", e.getMessage());
            return new TestResult(false, "连接 OSS 失败: " + e.getMessage());
        } finally {
            if (oss != null) {
                oss.shutdown();
            }
        }
    }

    private TestResult testCos(String region, String secretId, String secretKey, String bucket) {
        if (region == null || secretId == null || secretKey == null || bucket == null) {
            return new TestResult(false, "腾讯云 COS 需要填写 Region、Bucket、SecretId、SecretKey");
        }
        COSClient cos = null;
        try {
            COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
            ClientConfig clientConfig = new ClientConfig(new Region(region));
            clientConfig.setHttpProtocol(HttpProtocol.https);
            cos = new COSClient(credentials, clientConfig);
            boolean exists = cos.doesBucketExist(bucket);
            return exists
                    ? new TestResult(true, "连接成功，存储桶 " + bucket + " 可访问")
                    : new TestResult(false, "存储桶不存在或无权限: " + bucket);
        } catch (CosServiceException e) {
            log.warn("COS 连通性测试异常: {}", e.getMessage());
            return new TestResult(false, "COS 校验失败: " + e.getStatusCode() + " " + e.getErrorMessage());
        } catch (CosClientException e) {
            log.warn("COS 连通性测试异常: {}", e.getMessage());
            return new TestResult(false, "连接 COS 失败: " + e.getMessage());
        } catch (Exception e) {
            log.warn("COS 连通性测试异常: {}", e.getMessage());
            return new TestResult(false, "连接 COS 失败: " + e.getMessage());
        } finally {
            if (cos != null) {
                cos.shutdown();
            }
        }
    }

    /** 本地存储：只检查本机目录可写，不做云端同步 */
    private TestResult testLocal() {
        String root = System.getenv().getOrDefault("XINGSHU_UPLOAD_DIR",
                System.getProperty("user.home") + "/XingShuEnv/uploads");
        try {
            Path dir = Paths.get(root);
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, "storage-probe-", ".tmp");
            Files.deleteIfExists(probe);
            return new TestResult(true, "本地目录可写: " + dir + "（不启用云端同步）");
        } catch (Exception e) {
            log.warn("本地存储测试失败: {}", e.getMessage());
            return new TestResult(false, "本地目录不可写: " + e.getMessage());
        }
    }

    private String normalizeProvider(String provider) {
        String value = provider == null || provider.isBlank() ? "agc" : provider.trim().toLowerCase();
        if (!PROVIDERS.contains(value)) {
            throw new IllegalArgumentException("不支持的存储类型: " + provider);
        }
        return value;
    }

    private static String firstNonBlank(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }

    private record TestResult(boolean success, String message) {
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
