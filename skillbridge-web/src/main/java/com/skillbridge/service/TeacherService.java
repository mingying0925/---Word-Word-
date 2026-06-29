package com.skillbridge.service;

import com.skillbridge.model.Teacher;
import com.skillbridge.repository.TeacherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 教师认证服务。
 * <p>
 * 职责：
 * 1. 登录校验：工号 + 密码 → BCrypt 比对。
 * 2. 账号管理：创建教师账号（密码 BCrypt 哈希存储）。
 * 3. 初始化：首次启动时根据配置创建默认管理员账号。
 */
@Service
public class TeacherService {

    private static final Logger log = LoggerFactory.getLogger(TeacherService.class);

    private final TeacherRepository teacherRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.teacher.default-id:admin}")
    private String defaultTeacherId;

    @Value("${app.teacher.default-password:${TEACHER_DEFAULT_PASSWORD:ChangeMe123!}}")
    private String defaultPassword;

    @Value("${app.teacher.default-name:系统管理员}")
    private String defaultName;

    public TeacherService(TeacherRepository teacherRepository,
                          BCryptPasswordEncoder passwordEncoder) {
        this.teacherRepository = teacherRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 登录认证：校验工号与密码。
     *
     * @param teacherId 教师工号
     * @param password  明文密码
     * @return 认证成功返回 Teacher 实体，失败返回 empty
     */
    public Optional<Teacher> login(String teacherId, String password) {
        if (teacherId == null || password == null) {
            return Optional.empty();
        }
        return teacherRepository.findByTeacherId(teacherId)
                .filter(t -> t.getStatus() != null && t.getStatus() == 0)  // 账号启用
                .filter(t -> passwordEncoder.matches(password, t.getPasswordHash()));
    }

    /**
     * 创建教师账号。
     *
     * @param teacherId 工号（2-20 位字母或数字）
     * @param password  明文密码（将自动 BCrypt 哈希）
     * @param name      姓名
     * @return 已保存的教师实体
     */
    public Teacher createTeacher(String teacherId, String password, String name) {
        if (teacherId == null || !teacherId.matches("^[a-zA-Z0-9]{2,20}$")) {
            throw new BusinessException("工号格式不正确（2-20 位字母或数字）。");
        }
        if (password == null || password.length() < 6) {
            throw new BusinessException("密码长度不能少于 6 位。");
        }
        if (teacherRepository.existsByTeacherId(teacherId)) {
            throw new BusinessException("工号已存在: " + teacherId);
        }
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setPasswordHash(passwordEncoder.encode(password));
        teacher.setName(name);
        teacher.setStatus(0);
        return teacherRepository.save(teacher);
    }

    /**
     * 初始化默认管理员账号（应用启动时调用）。
     * 仅当数据库中无任何教师账号时创建，避免重复。
     */
    public void initDefaultTeacherIfNeeded() {
        if (teacherRepository.count() > 0) {
            return;
        }
        log.info("首次启动：创建默认管理员账号 [{}]", defaultTeacherId);
        createTeacher(defaultTeacherId, defaultPassword, defaultName);
        log.info("默认管理员账号创建成功。工号: {}, 姓名: {}", defaultTeacherId, defaultName);
    }

    /**
     * 查询所有教师账号（按创建时间降序）。
     */
    public List<Teacher> findAllTeachers() {
        return teacherRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 根据 ID 查询教师账号。
     */
    public Optional<Teacher> findById(Long id) {
        return teacherRepository.findById(id);
    }

    /**
     * 切换教师账号状态（启用 ↔ 禁用）。
     */
    public Teacher toggleStatus(Long id) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("教师账号不存在: " + id));
        teacher.setStatus(teacher.getStatus() == 0 ? 1 : 0);
        return teacherRepository.save(teacher);
    }

    /**
     * 重置教师密码。
     */
    public Teacher resetPassword(Long id, String newPassword) {
        Teacher teacher = teacherRepository.findById(id)
                .orElseThrow(() -> new BusinessException("教师账号不存在: " + id));
        teacher.setPasswordHash(passwordEncoder.encode(newPassword));
        return teacherRepository.save(teacher);
    }
}
