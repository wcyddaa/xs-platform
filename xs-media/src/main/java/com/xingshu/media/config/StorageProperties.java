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

    /**
     * 本地文件根目录（教材/课件/作业附件统一存放）。
     * 默认放在用户目录下的 XingShuEnv，与各服务的启动工作目录无关；
     * 可用环境变量 XINGSHU_UPLOAD_DIR 覆盖。
     */
    private String localRoot = System.getProperty("user.home") + java.io.File.separator
            + "XingShuEnv" + java.io.File.separator + "uploads";

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
        /**
         * AGC 凭据文件路径（外部文件，不进代码仓库）。
         * 可用环境变量 XINGSHU_AGC_CREDENTIALS 指定；
         * 未配置时回退到 classpath:agc-apiclient.json（仅兼容旧环境）。
         */
        private String credentialsFile;
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