package com.xingshu.config.dto;

import lombok.Data;

/**
 * 模型配置请求/响应 DTO
 */
@Data
public class ModelConfigDTO {

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
}