-- ============================================
-- xs-config 分层改造：模型测试/发布 + 两级额度
-- 说明：管理员维护模型并测试发布；教师按教学班创建智能体实例；
--       平台总额度 -> 教学班额度 -> 同班智能体共享。
-- ============================================

USE `xs-config`;

-- 1) 模型配置增加测试状态与发布开关
ALTER TABLE `model_config`
    ADD COLUMN `test_status` VARCHAR(20) NOT NULL DEFAULT 'untested' COMMENT '测试状态：untested-未测试, success-成功, fail-失败' AFTER `max_tokens`,
    ADD COLUMN `test_message` VARCHAR(500) DEFAULT NULL COMMENT '最近一次测试结果说明' AFTER `test_status`,
    ADD COLUMN `last_test_time` DATETIME DEFAULT NULL COMMENT '最近一次测试时间' AFTER `test_message`,
    ADD COLUMN `enabled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否发布可用：1-已发布, 0-未发布/停用' AFTER `last_test_time`;

-- 2) 平台总额度（单行记录，id 固定为 1）
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

-- 3) 教学班额度（每个教学班一条，班内全部智能体共享）
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

-- 4) 初始化平台总额度（可按需在管理端调整）
INSERT INTO `platform_quota` (`id`, `total_token_limit`, `token_used`)
SELECT 1, 10000000, 0
WHERE NOT EXISTS (SELECT 1 FROM `platform_quota` WHERE `id` = 1);

-- 5) 为已有智能体配置的教学班补齐额度，避免升级后不可用（每班 50 万 token）
INSERT INTO `class_quota` (`class_id`, `total_token_limit`, `token_used`)
SELECT DISTINCT c.`class_id`, 500000, 0
FROM `agent_class_config` c
LEFT JOIN `class_quota` q ON q.`class_id` = c.`class_id`
WHERE q.`id` IS NULL;
