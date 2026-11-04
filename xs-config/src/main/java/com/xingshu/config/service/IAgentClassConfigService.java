package com.xingshu.config.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingshu.config.entity.AgentClassConfig;

import java.util.List;
import java.util.Map;

/**
 * 教学班智能体配置服务接口
 */
public interface IAgentClassConfigService extends IService<AgentClassConfig> {

    /** 根据教学班ID查询配置（兼容旧接口，返回第一个启用的智能体） */
    AgentClassConfig getByClassId(Long classId);

    /** 查询教学班配置的全部智能体 */
    List<AgentClassConfig> listByClassId(Long classId);

    /** 根据教师ID查询所有配置 */
    List<AgentClassConfig> listByTeacherId(Long teacherId);

    /** 管理员查看全部实例（按教学班、实例排序） */
    List<AgentClassConfig> listAll();

    /** 保存或更新配置（按 id，或 classId + agentName 唯一） */
    AgentClassConfig saveOrUpdateConfig(AgentClassConfig config);

    /** 获取供 FastAPI 调用的运行时配置，超出限额时抛出 IllegalStateException */
    AgentClassConfig getRuntimeConfig(Long classId, String agentName);

    /** 上报用量：累计 token 与当日请求次数（同步扣减教学班额度与平台已用） */
    void reportUsage(Long id, int tokens);

    /** 查询教学班智能体用量汇总（含班级共享额度） */
    Map<String, Object> getClassUsage(Long classId);

    /** 测试已保存实例（服务端使用已发布模型的地址与密钥真实调用） */
    Map<String, Object> testInstance(Long id);

    /** 按教学班ID删除配置 */
    void deleteByClassId(Long classId);

    /** 按教师ID删除配置 */
    void deleteByTeacherId(Long teacherId);
}
