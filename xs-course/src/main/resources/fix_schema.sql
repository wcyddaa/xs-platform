-- ============================================
-- xs-course 数据库修复脚本
-- 修复字段名不匹配问题：create_by/update_by → creater_id/updater_id
-- ============================================

USE `xs-course`;

-- 1. class_info 表：添加 creater_id/updater_id，移除 create_by/update_by/deleted
ALTER TABLE `class_info`
    ADD COLUMN IF NOT EXISTS `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID' AFTER `description`,
    ADD COLUMN IF NOT EXISTS `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID' AFTER `creater_id`,
    DROP COLUMN IF EXISTS `create_by`,
    DROP COLUMN IF EXISTS `update_by`,
    DROP COLUMN IF EXISTS `deleted`;

-- 2. course 表：添加 creater_id/updater_id，移除 create_by/update_by/deleted
ALTER TABLE `course`
    ADD COLUMN IF NOT EXISTS `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID' AFTER `status`,
    ADD COLUMN IF NOT EXISTS `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID' AFTER `creater_id`,
    DROP COLUMN IF EXISTS `create_by`,
    DROP COLUMN IF EXISTS `update_by`,
    DROP COLUMN IF EXISTS `deleted`;

-- 3. class_student 表：添加 BaseEntity 需要的字段
ALTER TABLE `class_student`
    ADD COLUMN IF NOT EXISTS `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间' AFTER `join_time`,
    ADD COLUMN IF NOT EXISTS `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`,
    ADD COLUMN IF NOT EXISTS `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID' AFTER `update_time`,
    ADD COLUMN IF NOT EXISTS `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID' AFTER `creater_id`;

-- 4. 新增课程-教师关联表（多对多）
CREATE TABLE IF NOT EXISTS `course_teacher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `teacher_id` BIGINT NOT NULL COMMENT '教师ID',
    `teacher_name` VARCHAR(50) DEFAULT '' COMMENT '教师姓名（冗余）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_teacher` (`course_id`, `teacher_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-教师关联表';

-- 5. 新增课程-班级关联表（多对多）
CREATE TABLE IF NOT EXISTS `course_class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `class_name` VARCHAR(100) DEFAULT '' COMMENT '班级名称（冗余）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_class` (`course_id`, `class_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_class_id` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-班级关联表';