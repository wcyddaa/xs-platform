-- ============================================
-- xs-course 数据库迁移脚本
-- 配合实体关系重构：移除旧字段，新增多对多关联表
-- ============================================

USE `xs-course`;

-- 1. 移除 class_info 表的旧字段（取消老师-班级关系）
ALTER TABLE `class_info` 
  DROP COLUMN IF EXISTS `teacher_id`,
  DROP COLUMN IF EXISTS `teacher_name`,
  DROP INDEX IF EXISTS `idx_teacher_id`;

-- 2. 移除 course 表的旧字段（取消直接关联，改用多对多表）
ALTER TABLE `course` 
  DROP COLUMN IF EXISTS `class_id`,
  DROP COLUMN IF EXISTS `teacher_id`,
  DROP COLUMN IF EXISTS `teacher_name`,
  DROP INDEX IF EXISTS `idx_class_id`,
  DROP INDEX IF EXISTS `idx_teacher_id`;

-- 3. 新增课程-教师关联表（多对多）
CREATE TABLE IF NOT EXISTS `course_teacher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `teacher_id` BIGINT NOT NULL COMMENT '教师ID',
    `teacher_name` VARCHAR(50) DEFAULT '' COMMENT '教师姓名（冗余）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_teacher` (`course_id`, `teacher_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-教师关联表';

-- 4. 新增课程-班级关联表（多对多）
CREATE TABLE IF NOT EXISTS `course_class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `class_name` VARCHAR(100) DEFAULT '' COMMENT '班级名称（冗余）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_class` (`course_id`, `class_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_class_id` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-班级关联表';