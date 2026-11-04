package com.xingshu.media.storage.impl;

import com.xingshu.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 云存储空实现：未配置、仅本地或 SDK 不可用时使用，保证业务链路不中断。
 */
public class NoOpStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(NoOpStorageService.class);

    private final String reason;

    public NoOpStorageService(String reason) {
        this.reason = reason;
        log.info("云存储降级为空实现: {}", reason);
    }

    @Override
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
        log.debug("云存储未启用（{}），忽略上传: {}", reason, path);
        return path;
    }

    @Override
    public String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata) {
        log.debug("云存储未启用（{}），忽略上传: {}", reason, path);
        return path;
    }

    @Override
    public byte[] downloadFile(String path) {
        log.debug("云存储未启用（{}），无法下载: {}", reason, path);
        return null;
    }

    @Override
    public String generatePresignedUrl(String path, Duration expireDuration) {
        return path;
    }

    @Override
    public boolean deleteFile(String path) {
        return false;
    }

    @Override
    public boolean exists(String path) {
        return false;
    }

    @Override
    public List<String> listFiles(String prefix) {
        return Collections.emptyList();
    }
}
