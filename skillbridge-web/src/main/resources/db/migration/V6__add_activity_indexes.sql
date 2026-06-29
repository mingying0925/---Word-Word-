-- Flyway 迁移脚本 V6__add_activity_indexes.sql
-- 为活动表添加常用查询索引，避免全表扫描

ALTER TABLE activities
    ADD INDEX idx_activities_status (status),
    ADD INDEX idx_activities_deadline (deadline),
    ADD INDEX idx_activities_created_at (created_at);
