package com.skillbridge.repository;

import com.skillbridge.model.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    /** 根据工号查询教师账号（用于登录认证） */
    Optional<Teacher> findByTeacherId(String teacherId);

    /** 判断工号是否已存在 */
    boolean existsByTeacherId(String teacherId);

    /** 查询所有教师账号（按创建时间降序） */
    List<Teacher> findAllByOrderByCreatedAtDesc();
}
