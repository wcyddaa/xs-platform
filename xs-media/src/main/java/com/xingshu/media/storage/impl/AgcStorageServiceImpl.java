package com.xingshu.media.storage.impl;

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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Collections;
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

    private StorageManagement storageManagement;
    private boolean initialized = false;

    public AgcStorageServiceImpl(StorageProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    public void init() {
        var agc = properties.getAgc();
        String bucket = agc.getBucket();
        if (bucket == null || bucket.isBlank()) {
            log.warn("AGC 云存储未配置 bucket-name，将不可用");
            return;
        }
        try {
            Resource resource = resourceLoader.getResource("classpath:agc-apiclient.json");
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

    @Override
    @SuppressWarnings("null")
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
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