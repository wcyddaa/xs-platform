-- ============================================
-- xs-exam 考试/作业服务数据库初始化脚本
-- ============================================

CREATE DATABASE IF NOT EXISTS `xs-exam` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xs-exam`;

-- 模板作业表（老师发布）
CREATE TABLE IF NOT EXISTS `template_homework` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT DEFAULT NULL COMMENT '所属课程ID',
    `class_id` BIGINT DEFAULT NULL COMMENT '所属教学班ID',
    `chapter_id` BIGINT DEFAULT NULL COMMENT '关联章节ID（可选）',
    `start_page` INT DEFAULT NULL COMMENT '指定教材起始页码',
    `end_page` INT DEFAULT NULL COMMENT '指定教材结束页码',
    `title` VARCHAR(200) NOT NULL COMMENT '作业标题',
    `description` TEXT COMMENT '作业描述/内容',
    `markdown_content` MEDIUMTEXT COMMENT 'Markdown格式作业文件内容',
    `attachments` MEDIUMTEXT COMMENT '附件列表JSON: [{name,type,content}]',
    `teacher_id` BIGINT NOT NULL COMMENT '发布老师ID',
    `teacher_name` VARCHAR(50) DEFAULT '' COMMENT '发布老师姓名',
    `status` VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '状态：draft-草稿, published-已发布',
    `total_students` INT DEFAULT 0 COMMENT '作业发布时教学班总人数',
    `submitted_count` INT DEFAULT 0 COMMENT '已提交人数',
    `graded_count` INT DEFAULT 0 COMMENT '已批改人数',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_course_id` (`course_id`),
    INDEX `idx_class_id` (`class_id`),
    INDEX `idx_chapter_id` (`chapter_id`),
    INDEX `idx_teacher_id` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板作业表';

-- 学生实例作业表（每个学生针对模板作业的作答）
CREATE TABLE IF NOT EXISTS `student_homework` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `template_id` BIGINT NOT NULL COMMENT '模板作业ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `student_name` VARCHAR(50) DEFAULT '' COMMENT '学生姓名（冗余）',
    `answer` TEXT COMMENT '作答内容',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态：pending-待提交, submitted-已提交, graded-已批改',
    `score` DOUBLE DEFAULT NULL COMMENT '分数',
    `comment` VARCHAR(1000) DEFAULT '' COMMENT '老师评语',
    `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID',
    `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    INDEX `idx_template_id` (`template_id`),
    INDEX `idx_student_id` (`student_id`),
    UNIQUE KEY `uk_template_student` (`template_id`, `student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生实例作业表';

-- 班级作业关联表（班级绑定模板作业）
CREATE TABLE IF NOT EXISTS `class_homework` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '班级ID',
    `class_name` VARCHAR(100) DEFAULT '' COMMENT '班级名称（冗余）',
    `template_id` BIGINT NOT NULL COMMENT '模板作业ID',
    `assign_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_class_template` (`class_id`, `template_id`),
    INDEX `idx_class_id` (`class_id`),
    INDEX `idx_template_id` (`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班级作业关联表';