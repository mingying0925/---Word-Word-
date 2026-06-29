package com.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 教师账号实体。
 * 表名：teachers
 * <p>
 * 用于教师端真实认证：存储工号与 BCrypt 密码哈希。
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "teachers")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 教师工号（唯一） */
    @Column(nullable = false, unique = true, length = 32)
    private String teacherId;

    /** BCrypt 密码哈希 */
    @Column(nullable = false)
    private String passwordHash;

    /** 教师姓名 */
    @Column(nullable = false, length = 50)
    private String name;

    /** 0=启用, 1=禁用 */
    @Column(nullable = false)
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
