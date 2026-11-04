package com.xingshu.config.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.xingshu.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 教学班智能体配置实体
 * 一个教学班可配置多个智能体，由教师填写 API 密钥与调用限额
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("agent_class_config")
public class AgentClassConfig extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 教学班ID */
    private Long classId;

    /** 配置教师ID */
    private Long teacherId;

    /** 关联模型配置ID */
    private Long modelConfigId;

    /** 智能体名称 */
    private String agentName;

    /** 智能体类型：chat-对话, homework-作业 */
    private String agentType;

    /** 系统提示词 */
    private String systemPrompt;

    /** 角色定义 */
    private String roleDefinition;

    /** 模型API地址（教师自行填写） */
    private String baseUrl;

    /** 模型API密钥（教师自行填写） */
    private String apiKey;

    /** 模型名称 */
    private String modelName;

    /** 温度参数 */
    private Double temperature;

    /** 单次最大输出 token */
    private Integer maxTokens;

    /** 累计 token 上限，NULL 表示不限制（允许编辑时清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer tokenLimit;

    /** 已用 token */
    private Integer tokenUsed;

    /** 每日请求次数上限，NULL 表示不限制（允许编辑时清空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer requestLimitDaily;

    /** 当日已用请求次数 */
    private Integer requestUsed;

    /** 请求计数日期 */
    private LocalDate requestDate;

    /** 状态：active-启用, inactive-停用 */
    private String status;
}
