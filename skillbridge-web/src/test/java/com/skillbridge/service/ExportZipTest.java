package com.skillbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.model.Activity;
import com.skillbridge.model.Submission;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ActivityService.exportAllAsZip 方法单元测试。
 * <p>
 * 覆盖：正常导出、无提交记录、活动不存在、单个学生导出失败容错。
 */
class ExportZipTest {

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private PythonExportClient pythonExportClient;
    @Spy
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ActivityService activityService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(activityService, "uploadDir", tempDir.toString());
    }

    @Test
    @DisplayName("正常导出：有效活动与提交记录生成包含正确条目的 ZIP")
    void shouldExportZipWithCorrectEntries() throws IOException {
        Activity activity = createActivity(1L, "/tmp/template.docx");
        Submission sub1 = createSubmission(10L, activity, "2024001", "张三");
        Submission sub2 = createSubmission(11L, activity, "2024002", "李四");

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(submissionRepository.findByActivityIdWithActivity(1L)).thenReturn(List.of(sub1, sub2));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(sub1));
        when(submissionRepository.findById(11L)).thenReturn(Optional.of(sub2));
        when(pythonExportClient.fillWord(eq("/tmp/template.docx"), anyMap()))
                .thenReturn("docx-content".getBytes());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        activityService.exportAllAsZip(1L, out);

        List<String> entryNames = readZipEntries(out.toByteArray());
        assertEquals(2, entryNames.size());
        assertTrue(entryNames.contains("张三.docx"));
        assertTrue(entryNames.contains("李四.docx"));
    }

    @Test
    @DisplayName("无提交记录时抛出 BusinessException")
    void shouldThrowWhenNoSubmissions() {
        Activity activity = createActivity(1L, "/tmp/template.docx");
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(submissionRepository.findByActivityIdWithActivity(1L)).thenReturn(List.of());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.exportAllAsZip(1L, out));
        assertTrue(ex.getMessage().contains("暂无学生提交"));
    }

    @Test
    @DisplayName("活动不存在时抛出 BusinessException")
    void shouldThrowWhenActivityNotFound() {
        when(activityRepository.findById(99L)).thenReturn(Optional.empty());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.exportAllAsZip(99L, out));
        assertTrue(ex.getMessage().contains("活动不存在"));
    }

    @Test
    @DisplayName("单个学生导出失败时写入错误占位 txt 文件")
    void shouldWriteErrorPlaceholderWhenOneFails() throws IOException {
        Activity activity = createActivity(1L, "/tmp/template.docx");
        Submission sub1 = createSubmission(10L, activity, "2024001", "张三");
        Submission sub2 = createSubmission(11L, activity, "2024002", "李四");

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(submissionRepository.findByActivityIdWithActivity(1L)).thenReturn(List.of(sub1, sub2));
        when(submissionRepository.findById(10L)).thenReturn(Optional.of(sub1));
        when(submissionRepository.findById(11L)).thenReturn(Optional.of(sub2));
        // 张三导出成功,李四导出失败
        when(pythonExportClient.fillWord(anyString(), anyMap())).thenAnswer(inv -> {
            Map<String, String> data = inv.getArgument(1);
            if ("李四".equals(data.get("姓名"))) {
                throw new BusinessException("Python 导出失败");
            }
            return "docx-content".getBytes();
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        activityService.exportAllAsZip(1L, out);

        List<String> entryNames = readZipEntries(out.toByteArray());
        assertEquals(2, entryNames.size());
        assertTrue(entryNames.contains("张三.docx"));
        assertTrue(entryNames.contains("李四_导出失败.txt"));
    }

    private Activity createActivity(Long id, String templatePath) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setName("测试活动");
        activity.setTemplatePath(templatePath);
        activity.setStatus(0);
        return activity;
    }

    private Submission createSubmission(Long id, Activity activity, String studentId, String name) {
        Submission sub = new Submission();
        sub.setId(id);
        sub.setActivity(activity);
        sub.setStudentId(studentId);
        sub.setIdCard("440301199001011234");
        sub.setFormDataJson("{\"姓名\":\"" + name + "\"}");
        return sub;
    }

    private List<String> readZipEntries(byte[] zipBytes) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(
                new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
                zis.closeEntry();
            }
        }
        return names;
    }
}
