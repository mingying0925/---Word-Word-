-- Flyway 迁移脚本 V2__add_audit_logs.sql
-- 审计日志表：记录关键操作（登录、活动创建/删除/截止、导出等）

CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(64) NOT NULL COMMENT '操作人工号或标识',
    operator_role VARCHAR(16) NOT NULL COMMENT '操作人角色：teacher/student/system',
    action VARCHAR(64) NOT NULL COMMENT '操作类型：LOGIN/CREATE_ACTIVITY/DELETE_ACTIVITY/CLOSE_ACTIVITY/EXPORT/SUBMIT 等',
    target_type VARCHAR(32) COMMENT '操作对象类型：activity/submission/teacher/account',
    target_id VARCHAR(64) COMMENT '操作对象 ID',
    detail VARCHAR(512) COMMENT '操作详情（如活动名称、导出格式等）',
    ip VARCHAR(64) COMMENT '操作来源 IP',
    created_at DATETIME NOT NULL,
    INDEX idx_audit_operator (operator),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';
