package com.xingshu.media.storage.impl;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
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
 * 腾讯云 COS 存储实现。
 * <p>
 * 凭据由管理员在 xs-config「存储配置」中维护；未配置时回退 application.yaml 的
 * {@code xingshu.storage.cos.*}。
 */
public class CosStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(CosStorageServiceImpl.class);

    private final String region;
    private final String bucket;
    private final String secretId;
    private final String secretKey;

    private volatile COSClient client;

    public CosStorageServiceImpl(String region, String bucket, String secretId, String secretKey) {
        this.region = region;
        this.bucket = bucket;
        this.secretId = secretId;
        this.secretKey = secretKey;
        log.info("腾讯云 COS 存储已启用: region={}, bucket={}", region, bucket);
    }

    public CosStorageServiceImpl(StorageProperties properties) {
        this.region = properties.getCos().getRegion();
        this.bucket = properties.getCos().getBucket();
        this.secretId = properties.getCos().getSecretId();
        this.secretKey = properties.getCos().getSecretKey();
        log.info("腾讯云 COS 存储已启用（来自配置文件）: region={}, bucket={}", region, bucket);
    }

    private COSClient client() {
        COSClient current = client;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (client == null) {
                COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
                ClientConfig clientConfig = new ClientConfig(new Region(region));
                clientConfig.setHttpProtocol(HttpProtocol.https);
                client = new COSClient(credentials, clientConfig);
            }
            return client;
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
        try {
            return uploadFile(file.getBytes(), file.getContentType(), path, metadata);
        } catch (Exception e) {
            throw new IllegalStateException("COS 读取上传文件失败: " + path, e);
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
            metadata.forEach(objectMetadata::setHeader);
        }
        PutObjectRequest request = new PutObjectRequest(bucket, path,
                new ByteArrayInputStream(bytes), objectMetadata);
        client().putObject(request);
        return path;
    }

    @Override
    public byte[] downloadFile(String path) {
        try {
            COSObject object = client().getObject(bucket, path);
            try (InputStream input = object.getObjectContent()) {
                return input.readAllBytes();
            }
        } catch (Exception e) {
            log.warn("COS 下载失败: {} ({})", path, e.getMessage());
            return null;
        }
    }

    @Override
    public String generatePresignedUrl(String path, Duration expireDuration) {
        Date expiration = new Date(System.currentTimeMillis() + expireDuration.toMillis());
        return client().generatePresignedUrl(new GeneratePresignedUrlRequest(bucket, path)
                .withExpiration(expiration)).toString();
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
        ListObjectsRequest request = new ListObjectsRequest();
        request.setBucketName(bucket);
        request.setPrefix(prefix);
        ObjectListing listing = client().listObjects(request);
        return listing.getObjectSummaries().stream()
                .map(summary -> summary.getKey())
                .toList();
    }
}
