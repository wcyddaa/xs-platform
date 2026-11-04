package com.xingshu.media.storage.impl;

import com.xingshu.media.config.StorageConfigClient;
import com.xingshu.media.config.StorageProperties;
import com.xingshu.media.storage.StorageService;
import com.xingshu.media.storage.StorageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 存储路由：按 xs-config 中管理员选定的 provider 动态选择实现。
 * <p>
 * 支持 agc / oss / cos / local；配置变化后（缓存 30 秒）自动切换实现，无需重启业务服务。
 * 未在后台配置时，回退到 application.yaml 的 {@code xingshu.storage.type}。
 */
public class RoutingStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(RoutingStorageService.class);

    private static final String AGC_CLASS = "com.huawei.agconnect.server.commons.exception.AGCException";
    private static final String OSS_CLASS = "com.aliyun.oss.OSS";
    private static final String COS_CLASS = "com.qcloud.cos.COS";

    private final StorageProperties properties;
    private final ResourceLoader resourceLoader;
    private final StorageConfigClient configClient;
    private final String appName;
    private final String configServiceUrl;
    private final String internalToken;

    private volatile StorageService delegate;
    private volatile String delegateSignature = "";

    public RoutingStorageService(StorageProperties properties, ResourceLoader resourceLoader,
                                 StorageConfigClient configClient, String appName,
                                 String configServiceUrl, String internalToken) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.configClient = configClient;
        this.appName = appName;
        this.configServiceUrl = configServiceUrl;
        this.internalToken = internalToken;
    }

    private StorageService current() {
        StorageConfigClient.Snapshot snapshot = configClient.fetch();
        String signature = snapshot == null
                ? "fallback:" + properties.getType()
                : snapshot.signature();
        StorageService current = delegate;
        if (current != null && signature.equals(delegateSignature)) {
            return current;
        }
        synchronized (this) {
            if (delegate != null && signature.equals(delegateSignature)) {
                return delegate;
            }
            StorageService built = build(snapshot);
            delegate = built;
            delegateSignature = signature;
            return built;
        }
    }

    private StorageService build(StorageConfigClient.Snapshot snapshot) {
        if (snapshot == null) {
            return buildFromProperties();
        }
        return switch (snapshot.provider()) {
            case "local" -> new NoOpStorageService("管理员配置为仅本地存储，云端同步已关闭");
            case "oss" -> ClassUtils.isPresent(OSS_CLASS, null)
                    ? new OssStorageServiceImpl(snapshot.endpoint(), snapshot.bucket(),
                    snapshot.clientId(), snapshot.clientSecret())
                    : new NoOpStorageService("阿里云 OSS SDK 不可用（缺少 aliyun-sdk-oss 依赖）");
            case "cos" -> ClassUtils.isPresent(COS_CLASS, null)
                    ? new CosStorageServiceImpl(snapshot.region(), snapshot.bucket(),
                    snapshot.clientId(), snapshot.clientSecret())
                    : new NoOpStorageService("腾讯云 COS SDK 不可用（缺少 cos_api 依赖）");
            default -> buildAgc();
        };
    }

    private StorageService buildFromProperties() {
        StorageType type = properties.getType();
        log.info("未在 xs-config 配置存储，回退到本地配置: type={}", type);
        return switch (type) {
            case AGC -> buildAgc();
            case OSS -> ClassUtils.isPresent(OSS_CLASS, null)
                    ? new OssStorageServiceImpl(properties)
                    : new NoOpStorageService("阿里云 OSS SDK 不可用（缺少 aliyun-sdk-oss 依赖）");
            case COS -> ClassUtils.isPresent(COS_CLASS, null)
                    ? new CosStorageServiceImpl(properties)
                    : new NoOpStorageService("腾讯云 COS SDK 不可用（缺少 cos_api 依赖）");
            case S3 -> new NoOpStorageService("S3 存储尚未接入");
        };
    }

    private StorageService buildAgc() {
        if (!ClassUtils.isPresent(AGC_CLASS, null)) {
            return new NoOpStorageService("AGC SDK 不可用（缺少 agconnect-storage 依赖）");
        }
        AgcStorageServiceImpl agc = new AgcStorageServiceImpl(
                properties, resourceLoader, appName, configServiceUrl, internalToken);
        agc.init();
        return agc;
    }

    @Override
    public String uploadFile(MultipartFile file, String path, Map<String, String> metadata) {
        return current().uploadFile(file, path, metadata);
    }

    @Override
    public String uploadFile(byte[] bytes, String contentType, String path, Map<String, String> metadata) {
        return current().uploadFile(bytes, contentType, path, metadata);
    }

    @Override
    public byte[] downloadFile(String path) {
        return current().downloadFile(path);
    }

    @Override
    public String generatePresignedUrl(String path, Duration expireDuration) {
        return current().generatePresignedUrl(path, expireDuration);
    }

    @Override
    public boolean deleteFile(String path) {
        return current().deleteFile(path);
    }

    @Override
    public boolean exists(String path) {
        return current().exists(path);
    }

    @Override
    public List<String> listFiles(String prefix) {
        return current().listFiles(prefix);
    }
}
