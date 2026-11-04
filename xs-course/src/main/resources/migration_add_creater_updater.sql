-- ============================================
-- 数据库迁移：添加 creater_id 和 updater_id 列
-- ============================================
USE `xs-course`;

-- class_info 表：添加 creater_id、updater_id，修改 teacher_id 可为 NULL
ALTER TABLE `class_info`
    ADD COLUMN `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID' AFTER `teacher_name`,
    ADD COLUMN `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID' AFTER `creater_id`,
    MODIFY COLUMN `teacher_id` BIGINT DEFAULT NULL COMMENT '负责老师ID';

-- course 表：添加 creater_id、updater_id
ALTER TABLE `course`
    ADD COLUMN `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID' AFTER `status`,
    ADD COLUMN `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID' AFTER `creater_id`;

-- chapter 表：添加 creater_id、updater_id、start_page、end_page
ALTER TABLE `chapter`
    ADD COLUMN `creater_id` BIGINT DEFAULT NULL COMMENT '创建者ID' AFTER `sort_order`,
    ADD COLUMN `updater_id` BIGINT DEFAULT NULL COMMENT '更新者ID' AFTER `creater_id`,
    ADD COLUMN `start_page` INT DEFAULT NULL COMMENT '对应教材起始页码' AFTER `updater_id`,
    ADD COLUMN `end_page` INT DEFAULT NULL COMMENT '对应教材结束页码' AFTER `start_page`;