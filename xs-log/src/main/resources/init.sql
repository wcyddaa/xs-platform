CREATE DATABASE IF NOT EXISTS `xs-log` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `xs-log`;

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `operator_id` BIGINT NOT NULL COMMENT '操作者ID',
    `operator_name` VARCHAR(50) DEFAULT '' COMMENT '操作者姓名',
    `operator_ip` VARCHAR(50) DEFAULT '' COMMENT '操作IP',
    `user_agent` VARCHAR(500) DEFAULT '' COMMENT '用户代理',
    `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型：LOGIN/LOGOUT/CREATE/UPDATE/DELETE/APPROVE/REJECT',
    `target_type` VARCHAR(50) NOT NULL COMMENT '目标类型：USER/COURSE/CLASS/HOMEWORK/TEXTBOOK/COURSEWARE/CHAPTER',
    `target_id` VARCHAR(200) DEFAULT '' COMMENT '目标ID',
    `target_name` VARCHAR(200) DEFAULT '' COMMENT '目标名称',
    `status` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '状态：SUCCESS/FAIL',
    `error_msg` VARCHAR(1000) DEFAULT '' COMMENT '失败原因',
    `duration_ms` BIGINT DEFAULT 0 COMMENT '耗时(毫秒)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_operator_id` (`operator_id`),
    INDEX `idx_operation_type` (`operation_type`),
    INDEX `idx_target_type` (`target_type`),
    INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';