-- ============================================
-- xs-course 课程服务数据库初始化脚本（重构版）
-- 教学班：teacher 绑定多对一，student 多对多
-- 课程：绑定创建者，不绑定教师，课程-教学班多对多
-- ============================================

CREATE DATABASE IF NOT EXISTS `xs-course` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xs-course`;

-- 教学班信息表（原 class_info）
CREATE TABLE IF NOT EXISTS `class_info` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '教学班名称',
    `description` VARCHAR(500) DEFAULT '' COMMENT '教学班描述',
    `college` VARCHAR(100) DEFAULT '' COMMENT '所属学院',
    `teacher_id` BIGINT DEFAULT NULL COMMENT '绑定教师ID',
    `teacher_name` VARCHAR(50) DEFAULT '' COMMENT '教师姓名（冗余）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft-草稿, pending-待审核, approved-已通过',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班信息表';

-- 教学班-教师关联表（多对多）
CREATE TABLE IF NOT EXISTS `class_teacher` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `teacher_id` BIGINT NOT NULL COMMENT '教师ID（关联xs-user的user表，type=2）',
    `teacher_name` VARCHAR(50) DEFAULT '' COMMENT '教师姓名（冗余）',
    `is_owner` TINYINT DEFAULT 0 COMMENT '是否主教师：1-是, 0-否',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_teacher` (`class_id`, `teacher_id`),
    INDEX `idx_class_teacher_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班-教师关联表';

-- 教学班-学生关联表（多对多）
CREATE TABLE IF NOT EXISTS `class_student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID（关联xs-user的user表，type=3）',
    `student_name` VARCHAR(50) DEFAULT '' COMMENT '学生姓名（冗余）',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    INDEX `idx_class_student` (`class_id`, `student_id`),
    INDEX `idx_student_id` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='教学班-学生关联表';

-- 课程表（绑定创建者，不绑定教师）
CREATE TABLE IF NOT EXISTS `course` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(200) NOT NULL COMMENT '课程名称',
    `description` VARCHAR(1000) DEFAULT '' COMMENT '课程描述',
    `textbook_id` VARCHAR(64) DEFAULT '' COMMENT '教材ID（关联xs-book的教材）',
    `textbook_title` VARCHAR(200) DEFAULT '' COMMENT '教材名称（冗余）',
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '课程状态：draft-草稿, pending-待审核, approved-已通过',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID（课程负责人）',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_creater_id` (`creater_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程信息表';

-- 课程-教学班关联表（多对多）
CREATE TABLE IF NOT EXISTS `course_class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '课程ID',
    `class_id` BIGINT NOT NULL COMMENT '教学班ID',
    `class_name` VARCHAR(100) DEFAULT '' COMMENT '教学班名称（冗余）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_class` (`course_id`, `class_id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_class_id` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-教学班关联表';

-- 章节表（课程子表）
CREATE TABLE IF NOT EXISTS `chapter` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
    `title` VARCHAR(200) NOT NULL COMMENT '章节标题',
    `description` VARCHAR(1000) DEFAULT '' COMMENT '章节描述',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    `start_page` INT DEFAULT NULL COMMENT '对应教材起始页码',
    `end_page` INT DEFAULT NULL COMMENT '对应教材结束页码',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程章节表';

-- 课程知识图谱节点表（按课程维度）
CREATE TABLE IF NOT EXISTS `knowledge_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
    `node_key` VARCHAR(120) NOT NULL COMMENT '节点唯一键（课程内唯一）',
    `name` VARCHAR(200) NOT NULL COMMENT '节点名称',
    `category` VARCHAR(30) NOT NULL DEFAULT 'concept' COMMENT '节点类型：chapter/courseware/question/homework/concept',
    `description` VARCHAR(500) DEFAULT '' COMMENT '节点说明',
    `chapter_id` BIGINT DEFAULT NULL COMMENT '关联章节ID',
    `weight` DECIMAL(5,2) DEFAULT 1.00 COMMENT '权重（证据强度 0-1）',
    `source` VARCHAR(30) DEFAULT 'courseware' COMMENT '主要来源：courseware/question/homework/chapter',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_course_node` (`course_id`, `node_key`),
    INDEX `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识图谱节点表';

-- 课程知识图谱关系表
CREATE TABLE IF NOT EXISTS `knowledge_edge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
    `source_key` VARCHAR(120) NOT NULL COMMENT '起点节点键',
    `target_key` VARCHAR(120) NOT NULL COMMENT '终点节点键',
    `relation` VARCHAR(50) NOT NULL DEFAULT 'contains' COMMENT '关系：contains/prerequisite/related/weak_point',
    `weight` DECIMAL(5,2) DEFAULT 1.00 COMMENT '关系权重 0-1',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_source_key` (`source_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程知识图谱关系表';