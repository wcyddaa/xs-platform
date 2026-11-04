package com.xingshu.media.config;

import com.xingshu.media.storage.StorageType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 云存储配置属性
 * <p>
 * 配置示例：
 * <pre>
 * xingshu:
 *   storage:
 *     type: agc
 *     agc:
 *       app-id: ${AGC_APP_ID}
 *       secret: ${AGC_SECRET}
 *       bucket: pdf-dwad8
 *       region: cn-east-3
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "xingshu.storage")
public class StorageProperties {
    /** 存储类型：agc / oss / cos / s3 */
    private StorageType type = StorageType.AGC;

    /** 华为 AGC 配置 */
    private AgcConfig agc = new AgcConfig();

    /** 阿里云 OSS 配置 */
    private OssConfig oss = new OssConfig();

    /** 腾讯云 COS 配置 */
    private CosConfig cos = new CosConfig();

    /** AWS S3 配置 */
    private S3Config s3 = new S3Config();

    @Data
    public static class AgcConfig {
        private String appId;
        private String secret;
        private String bucket;
        private String region = "cn-east-3";
    }

    @Data
    public static class OssConfig {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class CosConfig {
        private String region;
        private String secretId;
        private String secretKey;
        private String bucket;
    }

    @Data
    public static class S3Config {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        private String region;
    }
}