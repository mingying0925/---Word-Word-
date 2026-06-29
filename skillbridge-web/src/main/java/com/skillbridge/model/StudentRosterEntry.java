package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 学生名单条目实体。
 * 表名：student_roster
 * <p>
 * 教师通过 Excel 批量导入学生名单后，每行对应一条记录。
 * 可选启用白名单校验：仅名单内学生可登录提交。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "student_roster",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_roster_activity_student",
                columnNames = {"activityId", "studentId"}))
public class StudentRosterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联活动 ID（不使用外键约束，避免删除活动时的级联问题） */
    @Column(nullable = false)
    private Long activityId;

    /** 学号 */
    @Column(nullable = false)
    private String studentId;

    /** 姓名 */
    @Column(nullable = false)
    private String studentName;

    /** 身份证号（数据库存储 AES-256-GCM 密文，读取时自动解密） */
    @Column(nullable = false)
    @Convert(converter = IdCardConverter.class)
    private String idCard;

    /** 班级（可选，从 Excel 第四列读取） */
    private String className;

    /** 导入时间 */
    private LocalDateTime importedAt;

    @PrePersist
    protected void onImport() {
        importedAt = LocalDateTime.now();
    }
}
