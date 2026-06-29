-- Flyway 迁移脚本 V3__add_template_library.sql
-- 模板库表：教师可复用的 Word 模板库
-- 支持从活动保存模板或直接上传新模板到库中

CREATE TABLE IF NOT EXISTS template_library (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '模板名称（教师自定义）',
    template_path VARCHAR(255) NOT NULL COMMENT '模板文件路径（本地 uploads/）',
    bookmarks_json TEXT COMMENT '书签坐标 JSON（来自 Python 解析）',
    field_count INT COMMENT '书签字段数量（冗余字段，便于列表展示）',
    created_at DATETIME COMMENT '创建时间',
    INDEX idx_template_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板库表';
