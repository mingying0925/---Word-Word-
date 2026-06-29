package com.skillbridge.service;

import com.skillbridge.model.Teacher;
import com.skillbridge.repository.TeacherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock
    private TeacherRepository teacherRepository;

    private TeacherService teacherService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        teacherService = new TeacherService(teacherRepository, encoder);
        // Inject @Value fields manually
        ReflectionTestUtils.setField(teacherService, "defaultTeacherId", "admin");
        ReflectionTestUtils.setField(teacherService, "defaultPassword", "admin123");
        ReflectionTestUtils.setField(teacherService, "defaultName", "系统管理员");
    }

    @Test
    @DisplayName("正确工号和密码登录成功")
    void shouldLoginSuccessfully() {
        String teacherId = "t001";
        String password = "securePass123";
        String hash = encoder.encode(password);
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setPasswordHash(hash);
        teacher.setStatus(0);

        when(teacherRepository.findByTeacherId(teacherId)).thenReturn(Optional.of(teacher));

        Optional<Teacher> result = teacherService.login(teacherId, password);
        assertTrue(result.isPresent());
        assertEquals(teacherId, result.get().getTeacherId());
    }

    @Test
    @DisplayName("错误密码登录失败")
    void shouldFailWithWrongPassword() {
        String teacherId = "t001";
        String hash = encoder.encode("correctPassword");
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setPasswordHash(hash);
        teacher.setStatus(0);

        when(teacherRepository.findByTeacherId(teacherId)).thenReturn(Optional.of(teacher));

        Optional<Teacher> result = teacherService.login(teacherId, "wrongPassword");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("不存在的工号登录失败")
    void shouldFailWithNonexistentId() {
        when(teacherRepository.findByTeacherId("nonexistent")).thenReturn(Optional.empty());

        Optional<Teacher> result = teacherService.login("nonexistent", "any");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("被禁用的账号登录失败")
    void shouldFailWithDisabledAccount() {
        String teacherId = "t001";
        String hash = encoder.encode("password");
        Teacher teacher = new Teacher();
        teacher.setTeacherId(teacherId);
        teacher.setPasswordHash(hash);
        teacher.setStatus(1); // 禁用

        when(teacherRepository.findByTeacherId(teacherId)).thenReturn(Optional.of(teacher));

        Optional<Teacher> result = teacherService.login(teacherId, "password");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("null 参数登录失败")
    void shouldFailWithNullParams() {
        assertTrue(teacherService.login(null, "password").isEmpty());
        assertTrue(teacherService.login("t001", null).isEmpty());
    }

    @Test
    @DisplayName("创建教师账号成功")
    void shouldCreateTeacher() {
        when(teacherRepository.existsByTeacherId("t001")).thenReturn(false);
        Teacher saved = new Teacher();
        saved.setTeacherId("t001");
        saved.setName("测试教师");
        when(teacherRepository.save(any(Teacher.class))).thenReturn(saved);

        Teacher result = teacherService.createTeacher("t001", "password", "测试教师");
        assertEquals("t001", result.getTeacherId());
        assertEquals("测试教师", result.getName());
        verify(teacherRepository).save(any(Teacher.class));
    }

    @Test
    @DisplayName("重复工号创建失败")
    void shouldFailDuplicateTeacherId() {
        when(teacherRepository.existsByTeacherId("t001")).thenReturn(true);

        assertThrows(BusinessException.class, () ->
                teacherService.createTeacher("t001", "password", "name"));
    }
}