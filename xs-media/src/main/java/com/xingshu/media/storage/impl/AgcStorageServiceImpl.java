package com.xingshu.media.storage.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.agconnect.server.commons.AGCClient;
import com.huawei.agconnect.server.commons.AGCParameter;
import com.huawei.agconnect.server.commons.credential.CredentialParser;
import com.huawei.agconnect.server.commons.credential.CredentialService;
import com.huawei.agconnect.server.commons.exception.AGCException;
import com.huawei.agconnect.server.cloud.storage.StorageManagement;
import com.huawei.agconnect.server.cloud.storage.request.DownloadFileRequest;
import com.huawei.agconnect.server.cloud.storage.service.AGCStorageManagement;
import com.huawei.agconnect.server.cloud.storage.service.StorageReference;
import com.xingshu.media.config.StorageProperties;
import com.xingshu.media.storage.StorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 华为 AGC 云存储实现
 */
public class AgcStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(AgcStorageServiceImpl.class);

    private final StorageProperties properties;
    private final ResourceLoader resourceLoader;

    @Value("${spring.application.name:xs-app}")
    private String appName;

    /** xs-config 地址：存储凭据由管理员在后台维护，服务启动时拉取 */
    @Value("${xingshu.storage.config-service-url:http://localhost:8086}")
    private String configServiceUrl;

    @Value("${xingshu.auth.internal-token:xingshu-internal-2026}")
    private String internalToken;

    private StorageManagement storageManagement;
    private boolean initialized = false;
    private long lastInitAttempt = 0L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AgcStorageServiceImpl(StorageProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        initialize();
    }

    /** 初始化：优先使用 xs-config 中的管理员配置，其次回退外部凭据文件 */
    private synchronized void initialize() {
        if (initialized) {
            return;
        }
        lastInitAttempt = System.currentTimeMillis();
        if (initializeFromConfigService()) {
            return;
        }
        initializeFromFile();
    }

    /** 从 xs-config 拉取管理员配置的存储凭据并初始化 */
    private boolean initializeFromConfigService() {
        var agc = properties.getAgc();
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(configServiceUrl + "/api/storage-config/internal"))
                    .header("X-Internal-Token", internalToken)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.debug("xs-config 存储配置不可用: HTTP {}", response.statusCode());
                return false;
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            String bucket = data.path("bucket").asText(null);
            String clientId = data.path("clientId").asText(null);
            String clientSecret = data.path("clientSecret").asText(null);
            if (bucket == null || bucket.isBlank() || clientId == null || clientId.isBlank()
                    || clientSecret == null || clientSecret.isBlank()) {
                log.info("xs-config 中尚未配置完整的存储凭据，回退本地凭据文件");
                return false;
            }
            String region = data.path("region").asText(agc.getRegion());
            Map<String, Object> credential = new LinkedHashMap<>();
            credential.put("type", "project_client_id");
            credential.put("client_id", clientId);
            credential.put("client_secret", clientSecret);
            credential.put("configuration_version", "3.0");
            credential.put("region", "CN");
            Path tempDir = Files.createTempDirectory("agc-credential-");
            Path credentialFile = tempDir.resolve("agc-apiclient.json");
            objectMapper.writeValue(credentialFile.toFile(), credential);

            CredentialService service = CredentialParser.toCredential(credentialFile.toFile());
            AGCParameter parameter = AGCParameter.builder().setCredential(service).build();
            AGCClient.initialize(appName, parameter, "CN");
            storageManagement = AGCStorageManagement.getInstance(AGCClient.getInstance(appName), region, bucket);
            initialized = true;
            log.info("AGC 云存储初始化成功（凭据来自 xs-config 数据库）: app={}, bucket={}, region={}",
                    appName, bucket, region);
            return true;
        } catch (Throwable e) {
            log.debug("从 xs-config 加载存储配置失败: {}", e.getMessage());
            return false;
        }
    }

    /** 回退方案：外部凭据文件 / 环境变量 / classpath */
    private void initializeFromFile() {
        var agc = properties.getAgc();
        String bucket = agc.getBucket();
        if (bucket == null || bucket.isBlank()) {
            log.warn("AGC 云存储未配置 bucket，将不可用");
            return;
        }
        try {
            Resource resource = resolveCredentialResource(agc.getCredentialsFile());
            if (resource == null) {
                log.warn("AGC 凭据未配置，云存储不可用；请在管理后台配置存储凭据");
                return;
            }
            File credentialFile;
            try {
                credentialFile = resource.getFile();
            } catch (IOException e) {
                log.debug("凭据文件在 JAR 内，使用临时文件加载");
                Path tempFile = Files.createTempFile("agc-apiclient-", ".json");
                try (InputStream is = resource.getInputStream()) {
                    Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }
                credentialFile = tempFile.toFile();
                credentialFile.deleteOnExit();
            }
            CredentialService credential = CredentialParser.toCredential(credentialFile);
            AGCParameter parameter = AGCParameter.builder()
                    .setCredential(credential)
                    .build();
            AGCClient.initialize(appName, parameter, "CN");
            storageManagement = AGCStorageManagement.getInstance(
                    AGCClient.getInstance(appName), agc.getRegion(), bucket);
            initialized = true;
            log.info("AGC 云存储初始化成功: app={}, bucket={}, region={}", appName, bucket, agc.getRegion());
        } catch (Throwable e) {
            log.error("AGC 云存储初始化失败: {}", e.getMessage(), e);
        }
    }

    /** 懒加载重试：xs-config 或网络恢复后无需重启服务 */
    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        if (System.currentTimeMillis() - lastInitAttempt < 30_000L) {
            return;
        }
        initialize();
    }

    /**
     * 凭据文件解析顺序：配置项 → 环境变量 → classpath（仅兼容旧环境）。
     * 凭据文件属于敏感信息，不应提交到代码仓库。
     */
    private Resource resolveCredentialResource(String configuredPath) {
        String path = configuredPath;
        if (path == null || path.isBlank()) {
            path = System.getenv("XINGSHU_AGC_CREDENTIALS");
        }
        if (path != null && !path.isBlank()) {
            FileSystemResource fileResource = new FileSystemResource(path);
            if (fileResource.exists()) {
                log.info("使用外部 AGC 凭据文件: {}", path);
                return fileResource;
            }
            log.warn("AGC 凭据文件不存在: {}", path);
            return null;
        }
        Resource classpathResource = resourceLoader.getResource("classpath:agc-apiclient.json");
        if (classpathResource.exists()) {
            log.warn("未配置外部 AGC 凭据，回退使用 classpath:agc-apiclient.json");
            return classpathResource;
        }
        return null;
    }

    @Override
    @SuppressWarnings("null")
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
        ensureInitialized();
        if (!initialized) {
            throw new RuntimeException("AGC 云存储不可用");
        }
        try {
            Path tempFile = Files.createTempFile("agc-upload-", ".tmp");
            file.transferTo(tempFile.toFile());
            try {
                StorageReference ref = storageManagement.getStorageReference(path);
                ref.putObject(path, tempFile.toFile());
                log.info("AGC 上传成功: {}", path);
                return path;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Throwable e) {
            log.error("AGC 上传失败: path={}, error={}", e.getMessage(), e);
            throw new RuntimeException("AGC 上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata) {
        ensureInitialized();
        if (!initialized) {
            throw new RuntimeException("AGC 云存储不可用");
        }
        try {
            Path tempFile = Files.createTempFile("agc-upload-", ".tmp");
            Files.write(tempFile, bytes);
            try {
                StorageReference ref = storageManagement.getStorageReference(path);
                ref.putObject(path, tempFile.toFile());
                log.info("AGC 上传成功: {}", path);
                return path;
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Throwable e) {
            log.error("AGC 上传失败: path={}, error={}", path, e.getMessage(), e);
            throw new RuntimeException("AGC 上传失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] downloadFile(String path) {
        ensureInitialized();
        if (!initialized) return null;
        try {
            StorageReference ref = storageManagement.getStorageReference(path);
            if (!ref.doesObjectExist()) {
                log.warn("AGC 文件不存在: {}", path);
                return null;
            }
            Path tempFile = Files.createTempFile("agc-download-", ".tmp");
            try {
                DownloadFileRequest request = new DownloadFileRequest();
                request.setOffset(0);
                request.setSaveFile(tempFile.toFile());
                ref.download(request);
                return Files.readAllBytes(tempFile);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (Throwable e) {
            log.error("AGC 下载失败: path={}, error={}", path, e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String generatePresignedUrl(String path, Duration expireDuration) {
        // AGC Server SDK 暂不支持预签名 URL，返回原始路径
        log.warn("AGC 暂不支持 generatePresignedUrl，返回原始路径: {}", path);
        return path;
    }

    @Override
    public boolean deleteFile(String path) {
        ensureInitialized();
        if (!initialized) return false;
        try {
            StorageReference ref = storageManagement.getStorageReference(path);
            if (ref.doesObjectExist()) {
                ref.deleteObject();
                log.info("AGC 删除成功: {}", path);
                return true;
            }
            return false;
        } catch (Throwable e) {
            log.error("AGC 删除失败: path={}, error={}", path, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean exists(String path) {
        ensureInitialized();
        if (!initialized) return false;
        try {
            StorageReference ref = storageManagement.getStorageReference(path);
            return ref.doesObjectExist();
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public List<String> listFiles(String prefix) {
        // AGC Server SDK 暂不支持 listFiles
        log.warn("AGC 暂不支持 listFiles");
        return Collections.emptyList();
    }
}