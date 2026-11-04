package com.xingshu.config.controller;

import com.xingshu.common.base.ApiResponse;
import com.xingshu.config.dto.ModelConfigDTO;
import com.xingshu.config.entity.ModelConfig;
import com.xingshu.config.security.AuthContext;
import com.xingshu.config.service.IModelConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型配置管理 Controller（管理员维护并测试发布）。
 */
@RestController
@RequestMapping("/api/model-config")
public class ModelConfigController {

    private final IModelConfigService modelConfigService;

    public ModelConfigController(IModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    /**
     * 根据配置类型查询所有配置（密钥脱敏）
     */
    @GetMapping
    public ApiResponse<List<ModelConfig>> list(@RequestParam String configType) {
        AuthContext.requireAdmin();
        return ApiResponse.success(desensitize(modelConfigService.listByType(configType)));
    }

    /**
     * 根据配置类型和智能体查询（密钥脱敏）
     */
    @GetMapping("/by-agent")
    public ApiResponse<List<ModelConfig>> listByAgent(@RequestParam String configType,
                                                       @RequestParam String agentName) {
        AuthContext.requireAdmin();
        return ApiResponse.success(desensitize(modelConfigService.listByTypeAndAgent(configType, agentName)));
    }

    /**
     * 教师可选的已发布模型（仅测试成功且已发布，完全脱敏）
     */
    @GetMapping("/published")
    public ApiResponse<List<ModelConfig>> listPublished(@RequestParam(required = false) String agentType) {
        AuthContext.requireAdminOrTeacher();
        List<ModelConfig> configs = modelConfigService.listPublishedByAgent(agentType);
        configs.forEach(c -> {
            c.setApiKey(null);
            c.setBaseUrl(null);
            c.setApiKeyConfigured(null);
        });
        return ApiResponse.success(configs);
    }

    /**
     * 获取智能体名称列表（下拉框）
     */
    @GetMapping("/agent-names")
    public ApiResponse<List<String>> getAgentNames(@RequestParam String configType) {
        AuthContext.requireAdmin();
        return ApiResponse.success(modelConfigService.getAgentNames(configType));
    }

    /**
     * 获取模型名称列表（下拉框）
     */
    @GetMapping("/model-names")
    public ApiResponse<List<String>> getModelNames(@RequestParam String configType) {
        AuthContext.requireAdmin();
        return ApiResponse.success(modelConfigService.getModelNames(configType));
    }

    /**
     * 获取模型选项列表（下拉框）
     */
    @GetMapping("/model-options")
    public ApiResponse<List<String>> getModelOptions(@RequestParam String configType) {
        AuthContext.requireAdmin();
        return ApiResponse.success(modelConfigService.getModelOptions(configType));
    }

    /**
     * 保存或更新配置
     */
    @PostMapping
    public ApiResponse<ModelConfig> save(@RequestBody ModelConfigDTO dto) {
        AuthContext.requireAdmin();
        try {
            ModelConfig saved = modelConfigService.saveOrUpdateConfig(dto);
            saved.setApiKeyConfigured(saved.getApiKey() != null && !saved.getApiKey().isBlank());
            saved.setApiKey(null);
            return ApiResponse.success("保存成功", saved);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 根据ID查询配置（密钥脱敏）
     */
    @GetMapping("/{id}")
    public ApiResponse<ModelConfig> getById(@PathVariable Long id) {
        AuthContext.requireAdmin();
        ModelConfig config = modelConfigService.getById(id);
        if (config == null) {
            return ApiResponse.error(404, "配置不存在");
        }
        desensitize(config);
        return ApiResponse.success(config);
    }

    /**
     * 管理员测试连接（服务端使用已保存的地址与密钥真实调用，并记录测试状态）
     */
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        AuthContext.requireAdmin();
        try {
            Map<String, Object> result = modelConfigService.testConnection(id);
            boolean success = Boolean.TRUE.equals(result.get("success"));
            String message = String.valueOf(result.get("message"));
            return success ? ApiResponse.success(message, result) : ApiResponse.error(502, message);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 发布模型（必须测试成功）
     */
    @PutMapping("/{id}/publish")
    public ApiResponse<Map<String, Object>> publish(@PathVariable Long id) {
        AuthContext.requireAdmin();
        try {
            return ApiResponse.success("已发布", modelConfigService.setEnabled(id, true));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 停用模型
     */
    @PutMapping("/{id}/unpublish")
    public ApiResponse<Map<String, Object>> unpublish(@PathVariable Long id) {
        AuthContext.requireAdmin();
        try {
            return ApiResponse.success("已停用", modelConfigService.setEnabled(id, false));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        AuthContext.requireAdmin();
        modelConfigService.deleteConfig(id);
        return ApiResponse.success("删除成功", null);
    }

    /**
     * 检测智能体 - 检查FastAPI后端中实际存在的智能体
     */
    @PostMapping("/detect-agents")
    public ApiResponse<List<String>> detectAgents(@RequestParam String configType) {
        AuthContext.requireAdmin();
        return ApiResponse.success(modelConfigService.detectAgents(configType));
    }

    /**
     * 获取指定智能体和模型名称的配置详情（密钥脱敏）
     */
    @GetMapping("/detail")
    public ApiResponse<ModelConfig> getDetail(@RequestParam String configType,
                                               @RequestParam String agentName,
                                               @RequestParam String modelName) {
        AuthContext.requireAdmin();
        List<ModelConfig> configs = modelConfigService.listByTypeAndAgent(configType, agentName);
        ModelConfig match = configs.stream()
                .filter(c -> modelName.equals(c.getModelName()))
                .findFirst()
                .orElse(null);
        if (match != null) {
            desensitize(match);
            return ApiResponse.success(match);
        }
        return ApiResponse.error(404, "未找到匹配的配置");
    }

    /**
     * FastAPI调用：根据配置类型和智能体获取配置（内部接口，含真实地址与密钥）
     */
    @GetMapping("/agent-config")
    public ApiResponse<Map<String, Object>> getAgentConfig(@RequestParam String configType,
                                                            @RequestParam(required = false) String agentName) {
        AuthContext.requireInternal();
        List<ModelConfig> configs;
        if (agentName != null && !agentName.isEmpty()) {
            configs = modelConfigService.listByTypeAndAgent(configType, agentName);
        } else {
            configs = modelConfigService.listByType(configType);
        }

        if (configs.isEmpty()) {
            return ApiResponse.error(404, "未找到配置");
        }

        ModelConfig config = configs.get(0);
        Map<String, Object> result = new HashMap<>();
        result.put("modelName", config.getModelName());
        result.put("baseUrl", config.getBaseUrl());
        result.put("apiKey", config.getApiKey());
        result.put("temperature", config.getTemperature());
        result.put("maxTokens", config.getMaxTokens());
        return ApiResponse.success(result);
    }

    private List<ModelConfig> desensitize(List<ModelConfig> configs) {
        configs.forEach(this::desensitize);
        return configs;
    }

    private void desensitize(ModelConfig config) {
        config.setApiKeyConfigured(config.getApiKey() != null && !config.getApiKey().isBlank());
        config.setApiKey(null);
    }
}
