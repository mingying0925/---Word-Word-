package com.skillbridge.service;

import com.skillbridge.model.Activity;
import com.skillbridge.model.ExportTask;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.ExportTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.*;

class AsyncExportServiceTest {

    @Mock
    private ExportTaskRepository exportTaskRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityService activityService;
    @Mock
    private AsyncExportService self;

    @InjectMocks
    private AsyncExportService asyncExportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(asyncExportService, "exportTempDir", tempDir.toString());
    }

    @Nested
    @DisplayName("createTask")
    class CreateTask {

        @Test
        @DisplayName("活动存在时使用活动名称")
        void shouldUseActivityName() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setName("测试活动");
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(exportTaskRepository.save(any())).thenAnswer(returnsFirstArg());

            ExportTask result = asyncExportService.createTask("ZIP", 1L, "admin");

            assertEquals("ZIP", result.getType());
            assertEquals(1L, result.getActivityId());
            assertEquals("测试活动", result.getActivityName());
            assertEquals("admin", result.getOperator());
            assertEquals("PENDING", result.getStatus());
            verify(exportTaskRepository).save(any());
        }

        @Test
        @DisplayName("活动不存在时使用 ID 字符串")
        void shouldFallbackToId() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());
            when(exportTaskRepository.save(any())).thenAnswer(returnsFirstArg());

            ExportTask result = asyncExportService.createTask("EXCEL", 99L, "teacher1");

            assertEquals("99", result.getActivityName());
        }
    }

    @Nested
    @DisplayName("getTask / getTasksByOperator")
    class QueryTasks {

        @Test
        @DisplayName("getTask 返回任务")
        void shouldReturnTask() {
            ExportTask task = new ExportTask();
            task.setId(1L);
            when(exportTaskRepository.findById(1L)).thenReturn(Optional.of(task));

            assertEquals(task, asyncExportService.getTask(1L));
        }

        @Test
        @DisplayName("getTask 返回 null 当找不到")
        void shouldReturnNullWhenNotFound() {
            when(exportTaskRepository.findById(999L)).thenReturn(Optional.empty());

            assertNull(asyncExportService.getTask(999L));
        }

        @Test
        @DisplayName("getTasksByOperator 返回操作人任务列表")
        void shouldReturnTasksByOperator() {
            List<ExportTask> tasks = List.of(new ExportTask());
            when(exportTaskRepository.findByOperatorOrderByCreatedAtDesc("admin")).thenReturn(tasks);

            assertEquals(tasks, asyncExportService.getTasksByOperator("admin"));
        }
    }

    @Nested
    @DisplayName("executeZipExport")
    class ExecuteZipExport {

        @Test
        @DisplayName("ZIP 导出成功标记 SUCCESS")
        void shouldMarkSuccessOnZipExport() {
            ExportTask task = new ExportTask();
            task.setId(1L);
            when(exportTaskRepository.findById(1L)).thenReturn(Optional.of(task));
            when(exportTaskRepository.save(any())).thenReturn(task);
            doNothing().when(activityService).exportAllAsZip(anyLong(), any());

            asyncExportService.executeZipExport(1L, 10L);

            verify(activityService).exportAllAsZip(eq(10L), any());
            assertEquals("SUCCESS", task.getStatus());
            assertNotNull(task.getCompletedAt());
            assertNotNull(task.getResultFilePath());
            assertTrue(task.getResultFilePath().startsWith(tempDir.toString()));
        }

        @Test
        @DisplayName("ZIP 导出失败标记 FAILED")
        void shouldMarkFailedOnZipExportError() {
            ExportTask task = new ExportTask();
            task.setId(2L);
            when(exportTaskRepository.findById(2L)).thenReturn(Optional.of(task));
            when(exportTaskRepository.save(any())).thenReturn(task);
            doThrow(new RuntimeException("导出异常")).when(activityService).exportAllAsZip(anyLong(), any());

            asyncExportService.executeZipExport(2L, 10L);

            assertEquals("FAILED", task.getStatus());
            assertEquals("导出异常", task.getErrorMessage());
        }

        @Test
        @DisplayName("任务不存在时静默跳过")
        void shouldSkipWhenTaskNotFound() {
            when(exportTaskRepository.findById(999L)).thenReturn(Optional.empty());

            asyncExportService.executeZipExport(999L, 10L);

            verify(activityService, never()).exportAllAsZip(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("executeExcelExport")
    class ExecuteExcelExport {

        @Test
        @DisplayName("Excel 导出成功标记 SUCCESS")
        void shouldMarkSuccessOnExcelExport() {
            ExportTask task = new ExportTask();
            task.setId(3L);
            when(exportTaskRepository.findById(3L)).thenReturn(Optional.of(task));
            when(exportTaskRepository.save(any())).thenReturn(task);
            doNothing().when(activityService).exportExcel(anyLong(), any());

            asyncExportService.executeExcelExport(3L, 20L);

            verify(activityService).exportExcel(eq(20L), any());
            assertEquals("SUCCESS", task.getStatus());
            assertNotNull(task.getCompletedAt());
            assertTrue(task.getResultFilePath().startsWith(tempDir.toString()));
        }

        @Test
        @DisplayName("Excel 导出失败标记 FAILED")
        void shouldMarkFailedOnExcelExportError() {
            ExportTask task = new ExportTask();
            task.setId(4L);
            when(exportTaskRepository.findById(4L)).thenReturn(Optional.of(task));
            when(exportTaskRepository.save(any())).thenReturn(task);
            doThrow(new RuntimeException("Excel错误")).when(activityService).exportExcel(anyLong(), any());

            asyncExportService.executeExcelExport(4L, 20L);

            assertEquals("FAILED", task.getStatus());
            assertEquals("Excel错误", task.getErrorMessage());
        }
    }
}
