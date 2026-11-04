-- 行政班表
CREATE TABLE IF NOT EXISTS `admin_class` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `name` VARCHAR(100) NOT NULL COMMENT '行政班名称',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT COMMENT '创建者ID',
    `updater_id` BIGINT COMMENT '更新者ID',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行政班表';

-- 行政班学生关联表（一个学生只能属于一个行政班）
CREATE TABLE IF NOT EXISTS `admin_class_student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `class_id` BIGINT NOT NULL COMMENT '行政班ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater_id` BIGINT COMMENT '创建者ID',
    `updater_id` BIGINT COMMENT '更新者ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_student` (`student_id`),
    KEY `idx_class_id` (`class_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='行政班学生关联表';