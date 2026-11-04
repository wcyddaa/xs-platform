package com.xingshu.config.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 模型配置实体（由管理员维护，测试通过并发布后教师才可选用）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("model_config")
public class ModelConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 配置类型：LLM-大语言模型, EMBEDDING-文本转向量 */
    private String configType;

    /** 智能体名称 */
    private String agentName;

    /** 模型名称 */
    private String modelName;

    /** API地址 */
    private String baseUrl;

    /** API密钥 */
    private String apiKey;

    /** 温度参数 */
    private Double temperature;

    /** 最大上下文长度 */
    private Integer maxTokens;

    /** 测试状态：untested-未测试, success-成功, fail-失败 */
    private String testStatus;

    /** 最近一次测试结果说明 */
    private String testMessage;

    /** 最近一次测试时间 */
    private LocalDateTime lastTestTime;

    /** 是否发布可用：1-已发布, 0-未发布/停用 */
    private Integer enabled;

    /** 密钥是否已配置（不落库，仅用于前端脱敏展示） */
    @TableField(exist = false)
    private Boolean apiKeyConfigured;
}