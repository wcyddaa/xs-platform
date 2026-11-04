package com.xingshu.media.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ListObjectsV2Result;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.xingshu.media.config.StorageProperties;
import com.xingshu.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 阿里云 OSS 存储实现。
 * <p>
 * 凭据由管理员在 xs-config「存储配置」中维护；未配置时回退 application.yaml 的
 * {@code xingshu.storage.oss.*}。
 */
public class OssStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(OssStorageServiceImpl.class);

    private final String endpoint;
    private final String bucket;
    private final String accessKeyId;
    private final String accessKeySecret;

    private volatile OSS client;

    public OssStorageServiceImpl(String endpoint, String bucket, String accessKeyId, String accessKeySecret) {
        this.endpoint = endpoint;
        this.bucket = bucket;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
        log.info("阿里云 OSS 存储已启用: endpoint={}, bucket={}", endpoint, bucket);
    }

    public OssStorageServiceImpl(StorageProperties properties) {
        this.endpoint = properties.getOss().getEndpoint();
        this.bucket = properties.getOss().getBucket();
        this.accessKeyId = properties.getOss().getAccessKey();
        this.accessKeySecret = properties.getOss().getSecretKey();
        log.info("阿里云 OSS 存储已启用（来自配置文件）: endpoint={}, bucket={}", endpoint, bucket);
    }

    private OSS client() {
        OSS current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                client = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            }
            return client;
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
        try {
            return uploadFile(file.getBytes(), file.getContentType(), path, metadata);
        } catch (Exception e) {
            throw new IllegalStateException("OSS 读取上传文件失败: " + path, e);
        }
    }

    @Override
    public String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(bytes.length);
        if (contentType != null && !contentType.isBlank()) {
            objectMetadata.setContentType(contentType);
        }
        if (metadata != null) {
            metadata.forEach(objectMetadata::addUserMetadata);
        }
        client().putObject(bucket, path, new ByteArrayInputStream(bytes), objectMetadata);
        return path;
    }

    @Override
    public byte[] downloadFile(String path) {
        try (OSSObject object = client().getObject(bucket, path);
             InputStream input = object.getObjectContent()) {
            return input.readAllBytes();
        } catch (com.aliyun.oss.OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                return null;
            }
            throw new IllegalStateException("OSS 下载失败: " + path, e);
        } catch (Exception e) {
            throw new IllegalStateException("OSS 下载失败: " + path, e);
        }
    }

    @Override
    public String generatePresignedUrl(String path, Duration expireDuration) {
        Date expiration = new Date(System.currentTimeMillis() + expireDuration.toMillis());
        return client().generatePresignedUrl(bucket, path, expiration).toString();
    }

    @Override
    public boolean deleteFile(String path) {
        client().deleteObject(bucket, path);
        return true;
    }

    @Override
    public boolean exists(String path) {
        return client().doesObjectExist(bucket, path);
    }

    @Override
    public List<String> listFiles(String prefix) {
        ListObjectsV2Result result = client().listObjectsV2(bucket, prefix);
        return result.getObjectSummaries().stream()
                .map(summary -> summary.getKey())
                .toList();
    }
}
