-- ============================================
-- xs-learning 学习服务数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS `xs-learning` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xs-learning`;

-- 学生提问记录表
CREATE TABLE IF NOT EXISTS `student_question_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `student_name` VARCHAR(100) DEFAULT '' COMMENT '学生姓名',
    `question` TEXT COMMENT '提问内容',
    `answer` TEXT COMMENT 'AI回答内容',
    `topics` VARCHAR(500) DEFAULT '' COMMENT '提问主题/倾向标签',
    `tokens_used` INT DEFAULT 0 COMMENT '消耗Token数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    INDEX `idx_class_id` (`class_id`),
    INDEX `idx_student_id` (`student_id`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生提问记录表';

-- ============================================
-- 能力图谱相关表
-- ============================================

-- 能力维度定义表（绑定教学班 + 章节）
CREATE TABLE IF NOT EXISTS `ability_dimension` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `chapter_id` BIGINT DEFAULT NULL COMMENT '章节ID（可选）',
    `dimension_name` VARCHAR(100) NOT NULL COMMENT '能力维度名称',
    `dimension_code` VARCHAR(50) NOT NULL COMMENT '唯一编码',
    `weight` DECIMAL(5,2) DEFAULT 1.00 COMMENT '权重',
    `description` TEXT COMMENT '能力描述',
    `sort_order` INT DEFAULT 0 COMMENT '展示顺序',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_code` (`class_id`, `dimension_code`),
    INDEX `idx_class` (`class_id`),
    INDEX `idx_chapter` (`chapter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能力维度定义表';

-- 学生能力达成度表
CREATE TABLE IF NOT EXISTS `student_ability` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `dimension_id` BIGINT NOT NULL COMMENT '能力维度ID',
    `mastery_level` DECIMAL(5,2) DEFAULT 0.00 COMMENT '掌握度 0-100',
    `evidence_count` INT DEFAULT 0 COMMENT '证据数量',
    `comment` TEXT COMMENT '智能体维度评语',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_dimension` (`student_id`, `dimension_id`),
    INDEX `idx_class` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生能力达成度表';

-- 学生学情报告表（每个教学班每个学生一份，按教学班隔离）
CREATE TABLE IF NOT EXISTS `student_ability_report` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `overall_score` DECIMAL(5,2) DEFAULT 0.00 COMMENT '加权综合评分 0-100',
    `summary` TEXT COMMENT '智能体综合评语',
    `source` VARCHAR(20) DEFAULT 'agent' COMMENT '评估来源：agent/rule',
    `question_count` INT DEFAULT 0 COMMENT '参与评估的提问数',
    `homework_count` INT DEFAULT 0 COMMENT '参与评估的作业数',
    `homework_avg_score` DECIMAL(5,2) DEFAULT NULL COMMENT '作业平均分',
    `evaluated_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评估时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student_class` (`student_id`, `class_id`),
    INDEX `idx_class` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生学情报告表';

-- 能力证据表
CREATE TABLE IF NOT EXISTS `ability_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `dimension_id` BIGINT NOT NULL COMMENT '能力维度ID',
    `source_type` ENUM('homework', 'exam', 'chat', 'quiz') NOT NULL COMMENT '证据来源类型',
    `source_id` BIGINT NOT NULL COMMENT '关联来源ID',
    `score` DECIMAL(5,2) DEFAULT NULL COMMENT '本次得分',
    `evidence_text` TEXT COMMENT '证据描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    INDEX `idx_student_dim` (`student_id`, `dimension_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='能力证据表';