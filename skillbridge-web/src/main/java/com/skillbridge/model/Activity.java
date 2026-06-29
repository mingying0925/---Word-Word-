package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 活动实体。
 * 表名：activities
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 活动名称 */
    @Column(nullable = false)
    private String name;

    /** 创建该活动的教师工号（归属校验用） */
    @Column(name = "owner_id")
    private String ownerId;

    /** 上传的模板文件路径（存在本地 uploads/） */
    private String templatePath;

    /** 书签坐标 JSON（来自 Python） */
    @Column(columnDefinition = "TEXT")
    private String bookmarksJson;

    /** 报名截止时间 */
    private LocalDateTime deadline;

    /** 0=报名中, 1=已截止, 2=草稿（待教师确认字段） */
    private Integer status;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = 0;
        }
    }
}
