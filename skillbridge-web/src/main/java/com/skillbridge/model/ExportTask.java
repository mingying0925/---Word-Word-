package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 导出任务实体。
 * 表名：export_tasks
 * <p>
 * 记录异步导出任务的状态与结果文件路径，支持大文件导出不阻塞请求。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "export_tasks")
public class ExportTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 任务类型：ZIP / EXCEL */
    @Column(nullable = false, length = 16)
    private String type;

    /** 关联活动 ID */
    @Column(nullable = false)
    private Long activityId;

    /** 活动名称（冗余存储，便于任务列表展示） */
    @Column(length = 100)
    private String activityName;

    /** 发起人工号 */
    @Column(nullable = false, length = 64)
    private String operator;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED */
    @Column(nullable = false, length = 16)
    private String status;

    /** 导出结果文件路径（成功后填充） */
    @Column(length = 512)
    private String resultFilePath;

    /** 结果文件名（用于下载时设置 Content-Disposition） */
    @Column(length = 200)
    private String resultFileName;

    /** 失败原因（失败时填充） */
    @Column(length = 512)
    private String errorMessage;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }
}
