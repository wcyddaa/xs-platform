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
 * AWS S3 兼容存储实现（支持 MinIO 等 S3 兼容服务）
 * 待完善
 */
public class S3StorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageServiceImpl.class);

    public S3StorageServiceImpl() {
        log.info("AWS S3 存储实现已加载（功能待完善）");
    }

    @Override
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
        throw new UnsupportedOperationException("S3 上传功能待实现");
    }

    @Override
    public String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata) {
        throw new UnsupportedOperationException("S3 上传功能待实现");
    }

    @Override
    public byte[] downloadFile(String path) {
        throw new UnsupportedOperationException("S3 下载功能待实现");
    }

    @Override
    public String generatePresignedUrl(String path, Duration expireDuration) {
        throw new UnsupportedOperationException("S3 预签名 URL 功能待实现");
    }

    @Override
    public boolean deleteFile(String path) {
        throw new UnsupportedOperationException("S3 删除功能待实现");
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