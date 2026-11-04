package com.xingshu.config.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingshu.config.constant.AgentTemplates;
import com.xingshu.config.entity.AgentClassConfig;
import com.xingshu.config.entity.ModelConfig;
import com.xingshu.config.mapper.AgentClassConfigMapper;
import com.xingshu.config.security.SecretCipher;
import com.xingshu.config.service.IAgentClassConfigService;
import com.xingshu.config.service.IModelConfigService;
import com.xingshu.config.service.IQuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentClassConfigServiceImpl extends ServiceImpl<AgentClassConfigMapper, AgentClassConfig> implements IAgentClassConfigService {

    private static final Logger log = LoggerFactory.getLogger(AgentClassConfigServiceImpl.class);

    private final IModelConfigService modelConfigService;
    private final IQuotaService quotaService;
    private final ModelConnectionTester connectionTester;
    private final SecretCipher secretCipher;

    public AgentClassConfigServiceImpl(IModelConfigService modelConfigService,
                                       IQuotaService quotaService,
                                       ModelConnectionTester connectionTester,
                                       SecretCipher secretCipher) {
        this.modelConfigService = modelConfigService;
        this.quotaService = quotaService;
        this.connectionTester = connectionTester;
        this.secretCipher = secretCipher;
    }

    /** 启动时把历史上明文保存的实例密钥迁移为密文（幂等） */
    @PostConstruct
    public void encryptLegacyApiKeys() {
        try {
            for (AgentClassConfig row : list()) {
                String key = row.getApiKey();
                if (key != null && !key.isBlank() && !secretCipher.isEncrypted(key)) {
                    AgentClassConfig update = new AgentClassConfig();
                    update.setId(row.getId());
                    update.setApiKey(secretCipher.encrypt(key));
                    updateById(update);
                    log.info("已将智能体实例 {} 的明文密钥迁移为密文", row.getId());
                }
            }
        } catch (Exception e) {
            log.warn("历史实例密钥加密迁移失败（不影响启动）：{}", e.getMessage());
        }
    }

    @Override
    public AgentClassConfig getByClassId(Long classId) {
        LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentClassConfig::getClassId, classId)
                .eq(AgentClassConfig::getStatus, "active")
                .orderByAsc(AgentClassConfig::getId)
                .last("limit 1");
        return getOne(wrapper);
    }

    @Override
    public List<AgentClassConfig> listByClassId(Long classId) {
        LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentClassConfig::getClassId, classId)
                .orderByAsc(AgentClassConfig::getId);
        return list(wrapper);
    }

    @Override
    public List<AgentClassConfig> listByTeacherId(Long teacherId) {
        LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentClassConfig::getTeacherId, teacherId)
                .orderByDesc(AgentClassConfig::getUpdateTime);
        return list(wrapper);
    }

    @Override
    public List<AgentClassConfig> listAll() {
        LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(AgentClassConfig::getClassId)
                .orderByAsc(AgentClassConfig::getId);
        return list(wrapper);
    }

    @Override
    @Transactional
    public AgentClassConfig saveOrUpdateConfig(AgentClassConfig config) {
        if (config.getClassId() == null) {
            throw new IllegalArgumentException("智能体实例必须绑定教学班");
        }
        AgentTemplates.Template template = AgentTemplates.find(config.getAgentType());
        if (template == null) {
            throw new IllegalArgumentException("智能体类型必须使用平台内置模板");
        }
        if (config.getModelConfigId() == null) {
            throw new IllegalArgumentException("请选择管理员已发布的模型");
        }
        ModelConfig model = modelConfigService.getById(config.getModelConfigId());
        if (model == null || !"LLM".equals(model.getConfigType())) {
            throw new IllegalArgumentException("所选模型不存在");
        }
        if (!Integer.valueOf(1).equals(model.getEnabled()) || !"success".equals(model.getTestStatus())) {
            throw new IllegalArgumentException("所选模型尚未通过管理员测试并发布，暂不可使用");
        }

        // 同一教学班同一模板只允许一个实例
        LambdaQueryWrapper<AgentClassConfig> typeWrapper = new LambdaQueryWrapper<>();
        typeWrapper.eq(AgentClassConfig::getClassId, config.getClassId())
                .eq(AgentClassConfig::getAgentType, config.getAgentType());
        if (config.getId() != null) {
            typeWrapper.ne(AgentClassConfig::getId, config.getId());
        }
        if (count(typeWrapper) > 0) {
            throw new IllegalArgumentException("该教学班已创建「" + template.name() + "」，请直接编辑已有实例");
        }

        if (config.getAgentName() == null || config.getAgentName().isBlank()) {
            config.setAgentName(template.name());
        }
        if (config.getStatus() == null || config.getStatus().isBlank()) {
            config.setStatus("active");
        }

        // 模型凭据只允许来自管理员发布的模型，服务端注入，忽略客户端传入的地址与密钥
        config.setBaseUrl(model.getBaseUrl());
        config.setApiKey(model.getApiKey());
        config.setModelName(model.getModelName());
        if (config.getTemperature() == null) {
            config.setTemperature(model.getTemperature() == null ? 0.3 : model.getTemperature());
        }
        if (config.getMaxTokens() == null) {
            config.setMaxTokens(model.getMaxTokens() == null ? 4096 : model.getMaxTokens());
        }
        // 班级额度统一由额度模块管理，不再使用实例级累计限额
        config.setTokenLimit(null);

        AgentClassConfig existing = null;
        if (config.getId() != null) {
            existing = getById(config.getId());
        }
        if (existing == null) {
            LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentClassConfig::getClassId, config.getClassId())
                    .eq(AgentClassConfig::getAgentName, config.getAgentName());
            existing = getOne(wrapper);
        }

        if (existing != null) {
            config.setId(existing.getId());
            if (config.getTokenUsed() == null) {
                config.setTokenUsed(existing.getTokenUsed());
            }
            if (config.getRequestUsed() == null) {
                config.setRequestUsed(existing.getRequestUsed());
            }
            if (config.getRequestDate() == null) {
                config.setRequestDate(existing.getRequestDate());
            }
            updateById(config);
        } else {
            if (config.getTokenUsed() == null) {
                config.setTokenUsed(0);
            }
            if (config.getRequestUsed() == null) {
                config.setRequestUsed(0);
            }
            save(config);
        }
        return config;
    }

    @Override
    public AgentClassConfig getRuntimeConfig(Long classId, String agentName) {
        AgentClassConfig config;
        if (agentName != null && !agentName.isBlank()) {
            LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentClassConfig::getClassId, classId)
                    .eq(AgentClassConfig::getAgentName, agentName)
                    .eq(AgentClassConfig::getStatus, "active")
                    .last("limit 1");
            config = getOne(wrapper);
        } else {
            config = getByClassId(classId);
        }
        if (config == null) {
            return null;
        }
        // 运行时始终使用管理员当前发布的模型与密钥，避免实例中保存的旧凭据失效
        applyPublishedModel(config);
        checkLimit(config);
        return config;
    }

    /** 将实例绑定到管理员当前已发布的模型，未发布则拒绝运行 */
    private void applyPublishedModel(AgentClassConfig config) {
        if (config.getModelConfigId() == null) {
            throw new IllegalStateException("该智能体未绑定模型，请联系教师重新配置");
        }
        ModelConfig model = modelConfigService.getById(config.getModelConfigId());
        if (model == null
                || !Integer.valueOf(1).equals(model.getEnabled())
                || !"success".equals(model.getTestStatus())) {
            throw new IllegalStateException("该智能体使用的模型已被停用，请联系教师重新选择");
        }
        config.setBaseUrl(model.getBaseUrl());
        // 库中模型密钥为密文，运行时返回解密后的真实密钥
        config.setApiKey(secretCipher.decrypt(model.getApiKey()));
        config.setModelName(model.getModelName());
    }

    private void checkLimit(AgentClassConfig config) {
        Map<String, Object> quota = quotaService.getClassQuota(config.getClassId());
        if (!Boolean.TRUE.equals(quota.get("configured"))) {
            throw new IllegalStateException("该教学班尚未分配智能体额度，请联系教师设置");
        }
        if (quotaService.isExhausted(config.getClassId())) {
            throw new IllegalStateException("该教学班智能体额度已用尽，请联系教师调整额度");
        }
        LocalDate today = LocalDate.now();
        boolean isToday = today.equals(config.getRequestDate());
        if (config.getRequestLimitDaily() != null
                && isToday
                && config.getRequestUsed() != null
                && config.getRequestUsed() >= config.getRequestLimitDaily()) {
            throw new IllegalStateException("该智能体今日调用次数已达上限，请明日再试");
        }
    }

    @Override
    @Transactional
    public void reportUsage(Long id, int tokens) {
        AgentClassConfig config = getById(id);
        if (config == null) {
            return;
        }
        int tokenUsed = (config.getTokenUsed() == null ? 0 : config.getTokenUsed()) + Math.max(tokens, 0);
        LocalDate today = LocalDate.now();
        boolean isToday = today.equals(config.getRequestDate());
        int requestUsed = isToday
                ? (config.getRequestUsed() == null ? 0 : config.getRequestUsed()) + 1
                : 1;

        LambdaUpdateWrapper<AgentClassConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentClassConfig::getId, id)
                .set(AgentClassConfig::getTokenUsed, tokenUsed)
                .set(AgentClassConfig::getRequestUsed, requestUsed)
                .set(AgentClassConfig::getRequestDate, today);
        update(wrapper);

        quotaService.consume(config.getClassId(), Math.max(tokens, 0));
        log.info("智能体用量上报: id={}, classId={}, tokens={}, totalTokens={}, todayRequests={}",
                id, config.getClassId(), tokens, tokenUsed, requestUsed);
    }

    @Override
    public Map<String, Object> getClassUsage(Long classId) {
        List<AgentClassConfig> configs = listByClassId(classId);
        Map<String, Object> quota = quotaService.getClassQuota(classId);
        Map<String, Object> result = new HashMap<>();
        int tokenUsed = 0;
        int requestUsedToday = 0;
        LocalDate today = LocalDate.now();
        for (AgentClassConfig c : configs) {
            tokenUsed += c.getTokenUsed() == null ? 0 : c.getTokenUsed();
            if (today.equals(c.getRequestDate())) {
                requestUsedToday += c.getRequestUsed() == null ? 0 : c.getRequestUsed();
            }
        }
        // 班级内全部智能体共享同一份额度
        long classLimit = ((Number) quota.getOrDefault("totalTokenLimit", 0L)).longValue();
        long classUsed = ((Number) quota.getOrDefault("tokenUsed", 0L)).longValue();

        // 用量汇总接口不返回密钥
        configs.forEach(c -> {
            c.setApiKey(null);
            c.setBaseUrl(null);
        });
        result.put("agentCount", configs.size());
        result.put("tokenUsed", tokenUsed);
        result.put("tokenLimit", classLimit);
        result.put("requestUsedToday", requestUsedToday);
        result.put("classTokenLimit", classLimit);
        result.put("classTokenUsed", classUsed);
        result.put("classQuotaConfigured", quota.get("configured"));
        result.put("agents", configs);
        return result;
    }

    @Override
    public Map<String, Object> testInstance(Long id) {
        AgentClassConfig config = getById(id);
        if (config == null) {
            throw new IllegalArgumentException("智能体实例不存在");
        }
        applyPublishedModel(config);
        ModelConnectionTester.Result result = connectionTester.test(
                config.getBaseUrl(), config.getApiKey(), config.getModelName());
        Map<String, Object> data = new HashMap<>();
        data.put("success", result.success());
        data.put("latencyMs", result.latencyMs());
        data.put("message", result.message());
        data.put("reply", result.reply());
        data.put("modelName", config.getModelName());
        return data;
    }

    @Override
    public void deleteByClassId(Long classId) {
        LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentClassConfig::getClassId, classId);
        remove(wrapper);
    }

    @Override
    public void deleteByTeacherId(Long teacherId) {
        LambdaQueryWrapper<AgentClassConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentClassConfig::getTeacherId, teacherId);
        remove(wrapper);
    }
}
