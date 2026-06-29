package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审计日志实体。
 * 表名：audit_logs
 * <p>
 * 记录关键操作（登录、活动创建/删除/截止、导出等），用于安全审计与问题排查。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作人工号或标识 */
    @Column(nullable = false, length = 64)
    private String operator;

    /** 操作人角色：teacher/student/system */
    @Column(nullable = false, length = 16)
    private String operatorRole;

    /** 操作类型：LOGIN/CREATE_ACTIVITY/DELETE_ACTIVITY/CLOSE_ACTIVITY/EXPORT/SUBMIT 等 */
    @Column(nullable = false, length = 64)
    private String action;

    /** 操作对象类型：activity/submission/teacher/account */
    @Column(length = 32)
    private String targetType;

    /** 操作对象 ID */
    @Column(length = 64)
    private String targetId;

    /** 操作详情（如活动名称、导出格式等） */
    @Column(length = 512)
    private String detail;

    /** 操作来源 IP */
    @Column(length = 64)
    private String ip;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
