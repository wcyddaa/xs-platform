package com.xingshu.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.config.entity.ModelConfig;
import com.xingshu.config.dto.ModelConfigDTO;

import java.util.List;
import java.util.Map;

/**
 * 模型配置服务接口
 */
public interface IModelConfigService extends IService<ModelConfig> {

    /**
     * 根据配置类型查询所有配置
     */
    List<ModelConfig> listByType(String configType);

    /**
     * 根据配置类型和智能体名称查询
     */
    List<ModelConfig> listByTypeAndAgent(String configType, String agentName);

    /**
     * 教师可选的已发布模型（测试成功且 enabled=1）
     */
    List<ModelConfig> listPublishedByAgent(String agentType);

    /**
     * 获取所有不同的智能体名称列表
     */
    List<String> getAgentNames(String configType);

    /**
     * 获取所有不同的模型名称列表
     */
    List<String> getModelNames(String configType);

    /**
     * 获取指定类型的模型名称列表（下拉框用）
     */
    List<String> getModelOptions(String configType);

    /**
     * 保存或更新配置（地址/密钥/模型变更时自动回到未测试、未发布状态）
     */
    ModelConfig saveOrUpdateConfig(ModelConfigDTO dto);

    /**
     * 删除配置
     */
    void deleteConfig(Long id);

    /**
     * 管理员测试连接：使用已保存的地址与密钥真实调用，并落库测试状态
     */
    Map<String, Object> testConnection(Long id);

    /**
     * 发布或停用模型（发布前必须测试成功）
     */
    Map<String, Object> setEnabled(Long id, boolean enabled);

    /**
     * 检测智能体 - 检查FastAPI后端中实际存在的智能体
     * 返回可用的智能体名称列表，不可用的从Redis中删除
     */
    List<String> detectAgents(String configType);
}
