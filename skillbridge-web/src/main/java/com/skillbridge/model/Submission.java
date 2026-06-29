package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
/**
 * 报名提交实体。
 * 表名：submissions
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "submissions")
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    /** 学号 */
    @Column(nullable = false)
    private String studentId;

    /** 身份证号（数据库存储 AES-256-GCM 密文，读取时自动解密） */
    @Column(nullable = false)
    @Convert(converter = IdCardConverter.class)
    private String idCard;

    /** 学生填写的键值对 JSON */
    @Column(columnDefinition = "TEXT")
    private String formDataJson;

    private LocalDateTime submitTime;

    @PrePersist
    protected void onSubmit() {
        submitTime = LocalDateTime.now();
    }
}
