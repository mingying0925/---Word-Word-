package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模板库实体。
 * 表名：template_library
 * <p>
 * 教师可将常用 Word 模板保存到库中，创建活动时可直接从库中选择复用，
 * 无需每次重新上传和解析。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "template_library")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 模板名称（教师自定义，如"学生报名表模板"） */
    @Column(nullable = false)
    private String name;

    /** 创建该模板的教师工号（归属校验用） */
    @Column(name = "owner_id")
    private String ownerId;

    /** 模板文件路径（存在本地 uploads/） */
    @Column(nullable = false)
    private String templatePath;

    /** 书签坐标 JSON（来自 Python 解析） */
    @Column(columnDefinition = "TEXT")
    private String bookmarksJson;

    /** 书签字段数量（冗余字段，便于列表展示） */
    private Integer fieldCount;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (fieldCount == null) {
            fieldCount = 0;
        }
    }
}
