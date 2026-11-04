-- ============================================
-- xs-learning 能力图谱升级（按教学班隔离 + 智能体评分）
-- 在已有数据库上执行一次即可
-- ============================================
USE `xs-learning`;

-- 章节改为可选，新增展示顺序
ALTER TABLE `ability_dimension`
    MODIFY COLUMN `chapter_id` BIGINT DEFAULT NULL COMMENT '章节ID（可选）';

ALTER TABLE `ability_dimension`
    ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT '展示顺序';

ALTER TABLE `student_ability`
    ADD COLUMN `comment` TEXT COMMENT '智能体维度评语';

-- 学情报告（每个教学班、每个学生一份）
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
