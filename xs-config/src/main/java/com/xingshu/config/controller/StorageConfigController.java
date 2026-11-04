package com.xingshu.config.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.config.entity.StorageConfig;
import com.xingshu.config.security.AuthContext;
import com.xingshu.config.service.StorageConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 对象存储配置 Controller。
 * <p>
 * 管理员在后台维护云端存储凭据，凭据加密后只保存在本地数据库；
 * 内部接口供 xs-media 等业务服务读取，不对前端暴露明文。
 */
@RestController
@RequestMapping("/api/storage-config")
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    public StorageConfigController(StorageConfigService storageConfigService) {
        this.storageConfigService = storageConfigService;
    }

    /** 管理员查看配置（密钥脱敏） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        AuthContext.requireAdmin();
        return ApiResponse.success(storageConfigService.listAll().stream().map(this::toView).toList());
    }

    /** 管理员保存配置（密钥留空则沿用原值） */
    @PutMapping
    public ApiResponse<Map<String, Object>> save(@RequestBody StorageConfig config) {
        Long adminId = AuthContext.requireAdmin().userId();
        try {
            StorageConfig saved = storageConfigService.save(config, adminId);
            return ApiResponse.success(toView(saved));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 支持的存储类型（前端按此渲染表单，新增 provider 只改后端） */
    @GetMapping("/providers")
    public ApiResponse<List<Map<String, Object>>> providers() {
        AuthContext.requireAdmin();
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(provider("agc", "华为 AGC 云存储", null, "区域 Region", "存储桶", "Client ID", "Client Secret",
                "AGC 凭据用于换取访问令牌，桶名与区域在 AGC 控制台查看"));
        list.add(provider("oss", "阿里云 OSS", "Endpoint", null, "Bucket", "AccessKeyId", "AccessKeySecret",
                "Endpoint 形如 oss-cn-hangzhou.aliyuncs.com，不需要带 https://"));
        list.add(provider("cos", "腾讯云 COS", null, "地域 Region", "存储桶", "SecretId", "SecretKey",
                "Region 形如 ap-guangzhou，桶名需带 APPID 后缀"));
        list.add(provider("local", "仅本地磁盘", null, null, null, null, null,
                "文件只保存在本机 XingShuEnv\\uploads，不做云端同步"));
        return ApiResponse.success(list);
    }

    /** 管理员测试连通性 */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> test(@RequestBody StorageConfig config) {
        Long adminId = AuthContext.requireAdmin().userId();
        try {
            return ApiResponse.success(toView(storageConfigService.test(config, adminId)));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /** 内部服务读取当前启用的存储配置（含解密后的凭据） */
    @GetMapping("/internal")
    public ApiResponse<Map<String, Object>> internal() {
        AuthContext.requireInternal();
        StorageConfig config = storageConfigService.active();
        if (config == null) {
            return ApiResponse.error(404, "未配置启用的对象存储");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", config.getProvider());
        data.put("endpoint", config.getEndpoint());
        data.put("bucket", config.getBucket());
        data.put("region", config.getRegion());
        data.put("clientId", config.getClientId());
        data.put("clientSecret", config.getClientSecret());
        return ApiResponse.success(data);
    }

    private Map<String, Object> provider(String value, String label, String endpointLabel, String regionLabel,
                                         String bucketLabel, String clientIdLabel, String secretLabel, String hint) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("label", label);
        item.put("endpointLabel", endpointLabel);
        item.put("regionLabel", regionLabel);
        item.put("bucketLabel", bucketLabel);
        item.put("clientIdLabel", clientIdLabel);
        item.put("secretLabel", secretLabel);
        item.put("hint", hint);
        return item;
    }

    private Map<String, Object> toView(StorageConfig config) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", config.getId());
        view.put("provider", config.getProvider());
        view.put("endpoint", config.getEndpoint());
        view.put("bucket", config.getBucket());
        view.put("region", config.getRegion());
        view.put("clientId", config.getClientId());
        view.put("secretConfigured", config.getClientSecret() != null && !config.getClientSecret().isBlank());
        view.put("status", config.getStatus());
        view.put("testMessage", config.getTestMessage());
        view.put("lastTestTime", config.getLastTestTime());
        view.put("enabled", config.getEnabled());
        view.put("updateTime", config.getUpdateTime());
        return view;
    }
}
