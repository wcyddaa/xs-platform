package com.xingshu.media.config;

import com.xingshu.media.storage.MediaFileStore;
import com.xingshu.media.storage.StorageService;
import com.xingshu.media.storage.impl.RoutingStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

/**
 * 云存储自动配置。
 * <p>
 * 具体使用哪个云厂商由管理员在 xs-config「存储配置」中决定（agc / oss / cos / local），
 * 业务服务无需配置 xingshu.storage.type；未在后台配置时回退到本地配置文件。
 */
@AutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public StorageService storageService(StorageProperties properties,
                                         ResourceLoader resourceLoader,
                                         @Value("${spring.application.name:xs-app}") String appName,
                                         @Value("${xingshu.storage.config-service-url:http://localhost:8086}")
                                         String configServiceUrl,
                                         @Value("${xingshu.internal-token:xingshu-internal-2026}")
                                         String internalToken) {
        log.info("初始化云存储服务：provider 由 xs-config 决定，未配置时回退 type={}", properties.getType());
        StorageConfigClient configClient = new StorageConfigClient(configServiceUrl, internalToken);
        return new RoutingStorageService(properties, resourceLoader, configClient,
                appName, configServiceUrl, internalToken);
    }

    @Bean
    @ConditionalOnMissingBean
    public MediaFileStore mediaFileStore(StorageProperties properties, StorageService storageService) {
        return new MediaFileStore(properties, storageService);
    }
}
