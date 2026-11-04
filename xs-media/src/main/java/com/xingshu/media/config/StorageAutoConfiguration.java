package com.xingshu.media.config;

import com.xingshu.media.storage.MediaFileStore;
import com.xingshu.media.storage.StorageService;
import com.xingshu.media.storage.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 云存储自动配置
 * <p>
 * 根据 xingshu.storage.type 配置自动选择对应的存储实现。
 * 各实现类通过 ClassUtils.isPresent() 动态检查 SDK 是否存在，
 * 避免缺少对应 SDK 依赖时类加载失败（NoClassDefFoundError）。
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageAutoConfiguration.class);

    private static final String AGC_EXCEPTION_CLASS = "com.huawei.agconnect.server.commons.exception.AGCException";
    private static final String OSS_CLASS = "com.aliyun.oss.OSS";
    private static final String COS_CLASS = "com.qcloud.cos.COS";
    private static final String S3_CLASS = "software.amazon.awssdk.services.s3.S3Client";

    @Bean
    @ConditionalOnMissingBean
    public StorageService storageService(StorageProperties properties, ResourceLoader resourceLoader) {
        StorageType type = properties.getType();
        log.info("初始化云存储服务: type={}", type);
        return switch (type) {
            case AGC -> {
                if (ClassUtils.isPresent(AGC_EXCEPTION_CLASS, null)) {
                    yield new com.xingshu.media.storage.impl.AgcStorageServiceImpl(properties, resourceLoader);
                }
                log.warn("AGC SDK 不可用（缺少 agconnect-storage 依赖），云存储功能降级为 No-Op");
                yield new NoOpStorageService();
            }
            case OSS -> {
                if (ClassUtils.isPresent(OSS_CLASS, null)) {
                    yield new com.xingshu.media.storage.impl.OssStorageServiceImpl();
                }
                log.warn("OSS SDK 不可用（缺少 aliyun-sdk-oss 依赖），云存储功能降级为 No-Op");
                yield new NoOpStorageService();
            }
            case COS -> {
                if (ClassUtils.isPresent(COS_CLASS, null)) {
                    yield new com.xingshu.media.storage.impl.CosStorageServiceImpl();
                }
                log.warn("COS SDK 不可用（缺少 cos_api 依赖），云存储功能降级为 No-Op");
                yield new NoOpStorageService();
            }
            case S3 -> {
                if (ClassUtils.isPresent(S3_CLASS, null)) {
                    yield new com.xingshu.media.storage.impl.S3StorageServiceImpl();
                }
                log.warn("S3 SDK 不可用（缺少 s3 SDK 依赖），云存储功能降级为 No-Op");
                yield new NoOpStorageService();
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean
    public MediaFileStore mediaFileStore(StorageProperties properties, StorageService storageService) {
        return new MediaFileStore(properties, storageService);
    }

    /**
     * 兜底实现：当 SDK 依赖不可用时，所有操作返回安全默认值，避免服务启动失败
     */
    static class NoOpStorageService implements StorageService {

        @Override
        public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
            log.warn("云存储不可用，上传操作被忽略: {}", path);
            return path;
        }

        @Override
        public String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata) {
            log.warn("云存储不可用，上传操作被忽略: {}", path);
            return path;
        }

        @Override
        public byte[] downloadFile(String path) {
            log.warn("云存储不可用，下载操作返回 null: {}", path);
            return null;
        }

        @Override
        public String generatePresignedUrl(String path, Duration expireDuration) {
            log.warn("云存储不可用，预签名 URL 不可用: {}", path);
            return path;
        }

        @Override
        public boolean deleteFile(String path) {
            log.warn("云存储不可用，删除操作被忽略: {}", path);
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
}