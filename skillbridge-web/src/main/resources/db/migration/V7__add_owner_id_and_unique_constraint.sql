-- Flyway 迁移脚本 V7__add_owner_id_and_unique_constraint.sql
-- 1. 为 activities 与 template_library 增加 owner_id 列，支持资源归属校验
-- 2. 为 submissions 增加唯一约束，防止同一活动下学号+身份证号重复提交（竞态兜底）

-- 活动归属：记录创建该活动的教师工号
ALTER TABLE activities
    ADD COLUMN owner_id VARCHAR(64) AFTER name;

-- 模板归属：记录创建该模板的教师工号
ALTER TABLE template_library
    ADD COLUMN owner_id VARCHAR(64) AFTER name;

-- 提交唯一约束：同一活动下 (student_id, id_card) 唯一
-- 注意：id_card 列已存储 AES-256-GCM 密文，同密钥同明文产生同密文，可做唯一性比对
ALTER TABLE submissions
    ADD CONSTRAINT uk_submission_activity_student_idcard
    UNIQUE (activity_id, student_id, id_card);
