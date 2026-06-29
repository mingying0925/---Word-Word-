package com.skillbridge.model;

/**
 * 导出任务状态枚举。
 * <p>
 * 数据库 {@code export_tasks.status} 列存储大写字符串，与枚举名一一对应。
 * 替代散落在 {@code AsyncExportService} 的魔法字符串（"PENDING"/"RUNNING"/"SUCCESS"/"FAILED"）。
 */
public enum ExportTaskStatus {

    PENDING,
    RUNNING,
    SUCCESS,
    FAILED
}
