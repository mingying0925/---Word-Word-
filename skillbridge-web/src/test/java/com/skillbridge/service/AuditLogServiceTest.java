package com.skillbridge.service;

import com.skillbridge.model.AuditLog;
import com.skillbridge.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("record")
    class Record {

        @Test
        @DisplayName("记录完整审计日志")
        void shouldRecordFullLog() {
            auditLogService.record("admin", "teacher", "LOGIN",
                    "teacher", "admin", "登录成功", "127.0.0.1");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertEquals("admin", saved.getOperator());
            assertEquals("teacher", saved.getOperatorRole());
            assertEquals("LOGIN", saved.getAction());
            assertEquals("teacher", saved.getTargetType());
            assertEquals("admin", saved.getTargetId());
            assertEquals("登录成功", saved.getDetail());
            assertEquals("127.0.0.1", saved.getIp());
        }

        @Test
        @DisplayName("operator 为 null 时使用 unknown")
        void shouldDefaultOperatorToUnknown() {
            auditLogService.record(null, "system", "STARTUP", null, null, null, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertEquals("unknown", captor.getValue().getOperator());
            assertEquals("system", captor.getValue().getOperatorRole());
        }

        @Test
        @DisplayName("operatorRole 为 null 时使用 system")
        void shouldDefaultRoleToSystem() {
            auditLogService.record("admin", null, "LOGIN", null, null, null, null);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertEquals("system", captor.getValue().getOperatorRole());
        }

        @Test
        @DisplayName("Repository 异常时不向上传播")
        void shouldSwallowRepositoryException() {
            when(auditLogRepository.save(any())).thenThrow(new RuntimeException("DB down"));

            assertDoesNotThrow(() -> auditLogService.record("admin", "teacher", "LOGIN",
                    null, null, null, null));
        }
    }

    @Nested
    @DisplayName("getLogs")
    class GetLogs {

        @Test
        @DisplayName("返回分页结果")
        void shouldReturnPagedLogs() {
            AuditLog log1 = new AuditLog();
            log1.setId(1L);
            AuditLog log2 = new AuditLog();
            log2.setId(2L);
            Page<AuditLog> page = new PageImpl<>(List.of(log1, log2));
            when(auditLogRepository.findAllByOrderByCreatedAtDesc(any(PageRequest.class))).thenReturn(page);

            Page<AuditLog> result = auditLogService.getLogs(0, 20);

            assertEquals(2, result.getContent().size());
            assertEquals(1L, result.getContent().get(0).getId());
        }
    }

    @Nested
    @DisplayName("getLogsByOperator")
    class GetLogsByOperator {

        @Test
        @DisplayName("按操作人过滤")
        void shouldFilterByOperator() {
            AuditLog log = new AuditLog();
            log.setOperator("admin");
            Page<AuditLog> page = new PageImpl<>(List.of(log));
            when(auditLogRepository.findByOperatorOrderByCreatedAtDesc(eq("admin"), any(PageRequest.class)))
                    .thenReturn(page);

            Page<AuditLog> result = auditLogService.getLogsByOperator("admin", 0, 10);

            assertEquals(1, result.getContent().size());
            assertEquals("admin", result.getContent().get(0).getOperator());
        }
    }

    @Nested
    @DisplayName("getLogsByAction")
    class GetLogsByAction {

        @Test
        @DisplayName("按操作类型过滤")
        void shouldFilterByAction() {
            AuditLog log = new AuditLog();
            log.setAction("LOGIN");
            Page<AuditLog> page = new PageImpl<>(List.of(log));
            when(auditLogRepository.findByActionOrderByCreatedAtDesc(eq("LOGIN"), any(PageRequest.class)))
                    .thenReturn(page);

            Page<AuditLog> result = auditLogService.getLogsByAction("LOGIN", 0, 10);

            assertEquals(1, result.getContent().size());
            assertEquals("LOGIN", result.getContent().get(0).getAction());
        }
    }
}
