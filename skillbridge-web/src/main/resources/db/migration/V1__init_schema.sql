-- Flyway 初始迁移脚本 V1__init_schema.sql
-- 创建 SkillBridge 核心表结构

-- 教师账号表
CREATE TABLE IF NOT EXISTS teachers (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    teacher_id VARCHAR(32) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    status INT NOT NULL DEFAULT 0,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 活动表
CREATE TABLE IF NOT EXISTS activities (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    template_path VARCHAR(255),
    bookmarks_json TEXT,
    deadline DATETIME,
    status INT,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 报名提交表
CREATE TABLE IF NOT EXISTS submissions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    activity_id BIGINT NOT NULL,
    student_id VARCHAR(32) NOT NULL,
    id_card VARCHAR(255) NOT NULL,
    form_data_json TEXT,
    submit_time DATETIME,
    CONSTRAINT fk_submission_activity FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE,
    INDEX idx_submission_activity (activity_id),
    INDEX idx_submission_student (activity_id, student_id, id_card)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
