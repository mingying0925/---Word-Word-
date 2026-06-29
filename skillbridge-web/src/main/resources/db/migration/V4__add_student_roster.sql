-- Flyway 迁移脚本 V4__add_student_roster.sql
-- 学生名单表：教师通过 Excel 批量导入的学生名单
-- 启用白名单校验：仅名单内学生可登录提交

CREATE TABLE IF NOT EXISTS student_roster (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL COMMENT '关联活动 ID',
    student_id VARCHAR(32) NOT NULL COMMENT '学号',
    student_name VARCHAR(50) NOT NULL COMMENT '姓名',
    id_card VARCHAR(255) NOT NULL COMMENT '身份证号（AES-256-GCM 密文存储）',
    class_name VARCHAR(100) COMMENT '班级（可选）',
    imported_at DATETIME COMMENT '导入时间',
    CONSTRAINT uk_roster_activity_student UNIQUE (activity_id, student_id),
    INDEX idx_roster_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生名单表';
