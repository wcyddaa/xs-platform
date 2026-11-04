-- ============================================
-- xs-config 配置服务数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS `xs-config` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xs-config`;

-- 模型配置表
CREATE TABLE IF NOT EXISTS `model_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `config_type` VARCHAR(20) NOT NULL COMMENT '配置类型：LLM-大语言模型, EMBEDDING-文本转向量',
    `agent_name` VARCHAR(100) NOT NULL COMMENT '智能体名称（如 chat, homework）',
    `model_name` VARCHAR(200) NOT NULL COMMENT '模型名称（如 qwen3-max, BAAI/bge-m3）',
    `base_url` VARCHAR(500) NOT NULL COMMENT 'API地址',
    `api_key` VARCHAR(500) NOT NULL COMMENT 'API密钥',
    `temperature` DOUBLE DEFAULT 0.3 COMMENT '温度参数（仅LLM类型使用）',
    `max_tokens` INT DEFAULT 4096 COMMENT '最大上下文长度',
    `test_status` VARCHAR(20) NOT NULL DEFAULT 'untested' COMMENT '测试状态：untested-未测试, success-成功, fail-失败',
    `test_message` VARCHAR(500) DEFAULT NULL COMMENT '最近一次测试结果说明',
    `last_test_time` DATETIME DEFAULT NULL COMMENT '最近一次测试时间',
    `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否发布可用：1-已发布, 0-未发布/停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_agent_model` (`config_type`, `agent_name`, `model_name`),
    INDEX `idx_config_type` (`config_type`),
    INDEX `idx_agent_name` (`agent_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型配置表';

-- 教学班智能体配置表（一个教学班可配置多个智能体）
CREATE TABLE IF NOT EXISTS `agent_class_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `teacher_id` BIGINT NOT NULL COMMENT '配置教师ID',
    `model_config_id` BIGINT DEFAULT NULL COMMENT '关联模型配置ID',
    `agent_name` VARCHAR(100) DEFAULT 'class_agent' COMMENT '智能体名称',
    `agent_type` VARCHAR(50) DEFAULT 'chat' COMMENT '智能体类型：chat-对话, homework-作业',
    `system_prompt` TEXT COMMENT '系统提示词',
    `role_definition` VARCHAR(500) DEFAULT '' COMMENT '角色定义',
    `base_url` VARCHAR(500) DEFAULT '' COMMENT '模型API地址（教师自行填写）',
    `api_key` VARCHAR(500) DEFAULT '' COMMENT '模型API密钥（教师自行填写）',
    `model_name` VARCHAR(200) DEFAULT '' COMMENT '模型名称',
    `temperature` DOUBLE DEFAULT 0.3 COMMENT '温度参数',
    `max_tokens` INT DEFAULT 4096 COMMENT '单次最大输出token',
    `token_limit` INT DEFAULT NULL COMMENT '累计token上限，NULL表示不限制',
    `token_used` INT DEFAULT 0 COMMENT '已用token',
    `request_limit_daily` INT DEFAULT NULL COMMENT '每日请求次数上限，NULL表示不限制',
    `request_used` INT DEFAULT 0 COMMENT '当日已用请求次数',
    `request_date` DATE DEFAULT NULL COMMENT '请求计数日期',
    `status` VARCHAR(20) DEFAULT 'active' COMMENT '状态：active-启用, inactive-停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_agent` (`class_id`, `agent_name`),
    INDEX `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班智能体配置表';

-- 平台总额度（单行，id=1；教学班分配额度后剩余可分配额度减少）
CREATE TABLE IF NOT EXISTS `platform_quota` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `total_token_limit` BIGINT NOT NULL DEFAULT 0 COMMENT '平台总额度（token）',
    `token_used` BIGINT NOT NULL DEFAULT 0 COMMENT '全平台累计消耗 token',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台智能体总额度';

-- 教学班额度（每个教学班一条，班内全部智能体共享）
CREATE TABLE IF NOT EXISTS `class_quota` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `total_token_limit` BIGINT NOT NULL DEFAULT 0 COMMENT '教学班分配额度（token）',
    `token_used` BIGINT NOT NULL DEFAULT 0 COMMENT '教学班累计消耗 token',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_id` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班智能体额度';

INSERT INTO `platform_quota` (`id`, `total_token_limit`, `token_used`) VALUES (1, 10000000, 0)
ON DUPLICATE KEY UPDATE `total_token_limit` = VALUES(`total_token_limit`);

-- 插入默认模型配置（需管理员在管理端测试连接并发布后才可被教师选用）
INSERT INTO `model_config` (`config_type`, `agent_name`, `model_name`, `base_url`, `api_key`, `temperature`, `max_tokens`, `test_status`, `enabled`) VALUES
('LLM', 'chat', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 0.3, 4096, 'untested', 0),
('LLM', 'homework', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 0.3, 4096, 'untested', 0),
('LLM', 'knowledge_graph', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 0.3, 4096, 'untested', 0),
('LLM', 'student_insight', 'qwen-plus', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 0.3, 4096, 'untested', 0),
('EMBEDDING', 'default', 'BAAI/bge-m3', 'https://api.siliconflow.cn', '', 0.0, 0, 'untested', 0);
-- 对象存储配置表（平台级，仅管理员维护；client_secret 以 AES-GCM 密文保存）
CREATE TABLE IF NOT EXISTS `storage_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `provider` VARCHAR(20) NOT NULL DEFAULT 'agc' COMMENT '存储类型：agc-华为AGC',
    `bucket` VARCHAR(100) DEFAULT NULL COMMENT '存储桶名称',
    `region` VARCHAR(50) DEFAULT 'cn-east-3' COMMENT '区域',
    `client_id` VARCHAR(100) DEFAULT NULL COMMENT 'AGC client_id',
    `client_secret` VARCHAR(500) DEFAULT NULL COMMENT 'AGC client_secret（加密后存储）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'untested' COMMENT '测试状态：untested/success/fail',
    `test_message` VARCHAR(500) DEFAULT NULL COMMENT '最近一次测试结果说明',
    `last_test_time` DATETIME DEFAULT NULL COMMENT '最近一次测试时间',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：1-启用 0-停用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    INDEX `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对象存储配置表';
