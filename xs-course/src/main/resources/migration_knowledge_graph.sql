-- ============================================
-- 课程知识图谱表（按课程维度组织，教师与学生共享）
-- 数据来源：课件内容 + 学生提问主题 + 作业错误
-- ============================================
USE `xs-course`;

CREATE TABLE IF NOT EXISTS `knowledge_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `course_id` BIGINT NOT NULL COMMENT '所属课程ID',
    `node_key` VARCHAR(120) NOT NULL COMMENT '节点唯一键（课程内唯一）',
    `name` VARCHAR(200) NOT NULL COMMENT '节点名称',
    `category` VARCHAR(30) NOT NULL DEFAULT 'concept' COMMENT '节点类型：chapter-章节, courseware-课件, question-高频提问, homework-作业易错, concept-知识点',
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
