package com.xingshu.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.config.entity.ModelConfig;
import com.xingshu.config.dto.ModelConfigDTO;
import com.xingshu.config.mapper.ModelConfigMapper;
import com.xingshu.config.service.IModelConfigService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ModelConfigServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements IModelConfigService {

    private final ModelConnectionTester connectionTester;

    public ModelConfigServiceImpl(ModelConnectionTester connectionTester) {
        this.connectionTester = connectionTester;
    }

    @Override
    public List<ModelConfig> listByType(String configType) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getConfigType, configType)
                .orderByAsc(ModelConfig::getAgentName);
        return list(wrapper);
    }

    @Override
    public List<ModelConfig> listByTypeAndAgent(String configType, String agentName) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getConfigType, configType)
                .eq(ModelConfig::getAgentName, agentName)
                .orderByAsc(ModelConfig::getModelName);
        return list(wrapper);
    }

    @Override
    public List<ModelConfig> listPublishedByAgent(String agentType) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ModelConfig::getConfigType, "LLM")
                .eq(ModelConfig::getEnabled, 1)
                .eq(ModelConfig::getTestStatus, "success");
        if (agentType != null && !agentType.isBlank()) {
            wrapper.eq(ModelConfig::getAgentName, agentType);
        }
        wrapper.orderByAsc(ModelConfig::getModelName);
        return list(wrapper);
    }

    @Override
    public List<String> getAgentNames(String configType) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ModelConfig::getAgentName)
                .eq(ModelConfig::getConfigType, configType)
                .groupBy(ModelConfig::getAgentName);
        return list(wrapper).stream()
                .map(ModelConfig::getAgentName)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getModelNames(String configType) {
        LambdaQueryWrapper<ModelConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(ModelConfig::getModelName)
                .eq(ModelConfig::getConfigType, configType)
                .groupBy(ModelConfig::getModelName);
        return list(wrapper).stream()
                .map(ModelConfig::getModelName)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getModelOptions(String configType) {
        return getModelNames(configType);
    }

    @Override
    public ModelConfig saveOrUpdateConfig(ModelConfigDTO dto) {
        ModelConfig config = new ModelConfig();
        config.setId(dto.getId());
        config.setConfigType(dto.getConfigType());
        config.setAgentName(dto.getAgentName());
        config.setModelName(dto.getModelName());
        config.setBaseUrl(dto.getBaseUrl());
        config.setTemperature(dto.getTemperature());
        config.setMaxTokens(dto.getMaxTokens());

        if (dto.getId() != null && dto.getId() > 0) {
            ModelConfig existing = getById(dto.getId());
            if (existing == null) {
                throw new IllegalArgumentException("模型配置不存在");
            }
            boolean keyProvided = dto.getApiKey() != null && !dto.getApiKey().isBlank();
            // 未填写密钥时保留原密钥，避免管理端脱敏回显后覆盖
            config.setApiKey(keyProvided ? dto.getApiKey().trim() : existing.getApiKey());

            boolean endpointChanged = keyProvided
                    || !Objects.equals(existing.getBaseUrl(), dto.getBaseUrl())
                    || !Objects.equals(existing.getModelName(), dto.getModelName())
                    || !Objects.equals(existing.getAgentName(), dto.getAgentName())
                    || !Objects.equals(existing.getConfigType(), dto.getConfigType());
            if (endpointChanged) {
                // 凭据或模型变更后必须重新测试与发布
                config.setTestStatus("untested");
                config.setTestMessage(null);
                config.setLastTestTime(null);
                config.setEnabled(0);
            } else {
                config.setTestStatus(existing.getTestStatus());
                config.setTestMessage(existing.getTestMessage());
                config.setLastTestTime(existing.getLastTestTime());
                config.setEnabled(existing.getEnabled());
            }
            updateById(config);
            return getById(config.getId());
        }

        config.setTestStatus("untested");
        config.setEnabled(0);
        save(config);
        return config;
    }

    @Override
    public void deleteConfig(Long id) {
        removeById(id);
    }

    @Override
    public Map<String, Object> testConnection(Long id) {
        ModelConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }
        ModelConnectionTester.Result result = connectionTester.test(
                config.getBaseUrl(), config.getApiKey(), config.getModelName());

        ModelConfig update = new ModelConfig();
        update.setId(id);
        update.setTestStatus(result.success() ? "success" : "fail");
        update.setTestMessage(result.message());
        update.setLastTestTime(LocalDateTime.now());
        // 测试失败自动停用，避免教师选到不可用模型
        update.setEnabled(result.success() ? config.getEnabled() : 0);
        updateById(update);

        Map<String, Object> data = new HashMap<>();
        data.put("success", result.success());
        data.put("latencyMs", result.latencyMs());
        data.put("message", result.message());
        data.put("reply", result.reply());
        data.put("testStatus", update.getTestStatus());
        data.put("enabled", update.getEnabled());
        return data;
    }

    @Override
    public Map<String, Object> setEnabled(Long id, boolean enabled) {
        ModelConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("模型配置不存在");
        }
        if (enabled && !"success".equals(config.getTestStatus())) {
            throw new IllegalArgumentException("请先测试连接成功后再发布该模型");
        }
        ModelConfig update = new ModelConfig();
        update.setId(id);
        update.setEnabled(enabled ? 1 : 0);
        updateById(update);

        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("enabled", update.getEnabled());
        return data;
    }

    @Override
    public List<String> detectAgents(String configType) {
        // 获取数据库中该类型的所有智能体名称
        List<String> dbAgents = getAgentNames(configType);
        // 返回数据库中存在的智能体列表（实际检测由FastAPI端完成）
        return dbAgents;
    }
}
