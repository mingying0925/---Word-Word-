CREATE TABLE IF NOT EXISTS export_tasks (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(16)     NOT NULL COMMENT '任务类型：ZIP / EXCEL',
    activity_id     BIGINT          NOT NULL COMMENT '关联活动 ID',
    activity_name   VARCHAR(100)    COMMENT '活动名称（冗余）',
    operator        VARCHAR(64)     NOT NULL COMMENT '发起人工号',
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / RUNNING / SUCCESS / FAILED',
    result_file_path VARCHAR(512)   COMMENT '导出结果文件路径',
    result_file_name VARCHAR(200)   COMMENT '结果文件名',
    error_message   VARCHAR(512)    COMMENT '失败原因',
    created_at      DATETIME        COMMENT '创建时间',
    completed_at    DATETIME        COMMENT '完成时间',
    INDEX idx_export_tasks_activity (activity_id),
    INDEX idx_export_tasks_operator (operator),
    INDEX idx_export_tasks_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导出任务表';
