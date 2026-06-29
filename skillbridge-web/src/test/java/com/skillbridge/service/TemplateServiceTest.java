package com.skillbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.model.Activity;
import com.skillbridge.model.Template;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TemplateService 单元测试。
 * 覆盖：从活动保存模板、上传新模板、删除模板、从模板库准备待确认数据。
 */
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private PythonExportClient pythonExportClient;
    @Mock
    private FileStorageService fileStorageService;
    @Spy
    private final ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TemplateService templateService;

    @TempDir
    Path tempDir;

    private static final String BOOKMARKS_JSON =
            "{\"bookmarks\":[{\"name\":\"姓名\"},{\"name\":\"学号\"}],\"tables_structure\":[]}";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 默认行为：fileStorageService.resolveLocalPath 返回临时目录下的文件
        Path storedPath = tempDir.resolve("template.docx");
        when(fileStorageService.resolveLocalPath(anyString())).thenReturn(storedPath);
    }

    /* ===================== saveFromActivity ===================== */

    @Nested
    @DisplayName("saveFromActivity 从活动保存模板")
    class SaveFromActivity {

        @Test
        @DisplayName("模板名称为空时抛出异常")
        void shouldThrowWhenNameBlank() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromActivity(1L, "  ", "admin"));
            assertTrue(ex.getMessage().contains("名称"));
        }

        @Test
        @DisplayName("活动不存在时抛出异常")
        void shouldThrowWhenActivityNotFound() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromActivity(99L, "测试模板", "admin"));
            assertTrue(ex.getMessage().contains("活动不存在"));
        }

        @Test
        @DisplayName("活动缺少模板数据时抛出异常")
        void shouldThrowWhenActivityHasNoTemplate() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath(null);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromActivity(1L, "测试模板", "admin"));
            assertTrue(ex.getMessage().contains("模板数据"));
        }

        @Test
        @DisplayName("源模板文件不存在时抛出异常")
        void shouldThrowWhenSourceFileMissing() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath("/missing/path.docx");
            activity.setBookmarksJson(BOOKMARKS_JSON);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            // resolveLocalPath 返回不存在的路径
            when(fileStorageService.resolveLocalPath("/missing/path.docx"))
                    .thenReturn(tempDir.resolve("nonexistent.docx"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromActivity(1L, "测试模板", "admin"));
            assertTrue(ex.getMessage().contains("源模板文件不存在"));
        }

        @Test
        @DisplayName("成功从活动保存模板")
        void shouldSaveFromActivitySuccessfully() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath("/source/path.docx");
            activity.setBookmarksJson(BOOKMARKS_JSON);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            // 创建源文件以便复制
            Path sourceFile = tempDir.resolve("source.docx");
            Files.writeString(sourceFile, "fake-docx");
            when(fileStorageService.resolveLocalPath("/source/path.docx")).thenReturn(sourceFile);

            when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
                Template t = inv.getArgument(0);
                t.setId(10L);
                return t;
            });

            Template result = templateService.saveFromActivity(1L, "测试模板", "admin");

            assertNotNull(result);
            assertEquals("测试模板", result.getName());
            assertEquals(BOOKMARKS_JSON, result.getBookmarksJson());
            assertEquals(2, result.getFieldCount());
            assertNotNull(result.getTemplatePath());
            verify(templateRepository).save(any(Template.class));
        }
    }

    /* ===================== saveFromUpload ===================== */

    @Nested
    @DisplayName("saveFromUpload 上传新模板")
    class SaveFromUpload {

        @Test
        @DisplayName("模板名称为空时抛出异常")
        void shouldThrowWhenNameBlank() {
            MultipartFile file = new MockMultipartFile("file", "t.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "data".getBytes());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromUpload("", file, "admin"));
            assertTrue(ex.getMessage().contains("名称"));
        }

        @Test
        @DisplayName("文件为空时抛出异常")
        void shouldThrowWhenFileEmpty() {
            MultipartFile file = new MockMultipartFile("file", "empty.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[0]);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromUpload("测试模板", file, "admin"));
            assertTrue(ex.getMessage().contains("上传"));
        }

        @Test
        @DisplayName("非 .docx 文件抛出异常")
        void shouldThrowWhenNotDocx() {
            MultipartFile file = new MockMultipartFile("file", "template.txt",
                    "text/plain", "hello".getBytes());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromUpload("测试模板", file, "admin"));
            assertTrue(ex.getMessage().contains(".docx"));
        }

        @Test
        @DisplayName("Python 解析失败时清理文件并抛出异常")
        void shouldCleanUpFileWhenParseFails() {
            MultipartFile file = new MockMultipartFile("file", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake-docx".getBytes());
            when(fileStorageService.store(any())).thenReturn("/stored/path.docx");
            when(pythonExportClient.parseBookmarks(any())).thenThrow(new BusinessException("Python 解析失败"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.saveFromUpload("测试模板", file, "admin"));
            assertTrue(ex.getMessage().contains("Python 解析失败"));
            verify(fileStorageService).delete("/stored/path.docx");
            verify(templateRepository, never()).save(any());
        }

        @Test
        @DisplayName("成功上传并保存模板")
        void shouldSaveFromUploadSuccessfully() {
            MultipartFile file = new MockMultipartFile("file", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake-docx".getBytes());
            when(fileStorageService.store(any())).thenReturn("/stored/path.docx");
            when(pythonExportClient.parseBookmarks(any())).thenReturn(BOOKMARKS_JSON);
            when(templateRepository.save(any(Template.class))).thenAnswer(inv -> {
                Template t = inv.getArgument(0);
                t.setId(20L);
                return t;
            });

            Template result = templateService.saveFromUpload("上传模板", file, "admin");

            assertNotNull(result);
            assertEquals("上传模板", result.getName());
            assertEquals(BOOKMARKS_JSON, result.getBookmarksJson());
            assertEquals(2, result.getFieldCount());
            verify(templateRepository).save(any(Template.class));
        }
    }

    /* ===================== delete ===================== */

    @Nested
    @DisplayName("delete 删除模板")
    class Delete {

        @Test
        @DisplayName("模板不存在时抛出异常")
        void shouldThrowWhenTemplateNotFound() {
            when(templateRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.delete(99L, "admin"));
            assertTrue(ex.getMessage().contains("模板不存在"));
        }

        @Test
        @DisplayName("成功删除模板及其文件")
        void shouldDeleteTemplateAndFile() {
            Template template = new Template();
            template.setId(1L);
            template.setTemplatePath("/stored/path.docx");
            when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

            templateService.delete(1L, "admin");

            verify(fileStorageService).delete("/stored/path.docx");
            verify(templateRepository).delete(template);
        }

        @Test
        @DisplayName("模板路径为空时仅删除数据库记录")
        void shouldDeleteOnlyDbRecordWhenPathNull() {
            Template template = new Template();
            template.setId(1L);
            template.setTemplatePath(null);
            when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

            templateService.delete(1L, "admin");

            verify(fileStorageService, never()).delete(anyString());
            verify(templateRepository).delete(template);
        }
    }

    /* ===================== preparePendingFromLibrary ===================== */

    @Nested
    @DisplayName("preparePendingFromLibrary 从模板库准备待确认数据")
    class PreparePendingFromLibrary {

        @Test
        @DisplayName("模板不存在时抛出异常")
        void shouldThrowWhenTemplateNotFound() {
            when(templateRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.preparePendingFromLibrary(99L));
            assertTrue(ex.getMessage().contains("模板不存在"));
        }

        @Test
        @DisplayName("模板数据不完整时抛出异常")
        void shouldThrowWhenTemplateDataIncomplete() {
            Template template = new Template();
            template.setId(1L);
            template.setTemplatePath(null);
            template.setBookmarksJson(BOOKMARKS_JSON);
            when(templateRepository.findById(1L)).thenReturn(Optional.of(template));
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> templateService.preparePendingFromLibrary(1L));
            assertTrue(ex.getMessage().contains("数据不完整"));
        }

        @Test
        @DisplayName("成功准备待确认数据（复制文件 + 返回 bookmarksJson）")
        void shouldPreparePendingSuccessfully() throws Exception {
            Template template = new Template();
            template.setId(1L);
            template.setTemplatePath("/source/path.docx");
            template.setBookmarksJson(BOOKMARKS_JSON);
            when(templateRepository.findById(1L)).thenReturn(Optional.of(template));

            // 创建源文件以便复制
            Path sourceFile = tempDir.resolve("source.docx");
            Files.writeString(sourceFile, "fake-docx");
            when(fileStorageService.resolveLocalPath("/source/path.docx")).thenReturn(sourceFile);

            ActivityService.PendingTemplate pending = templateService.preparePendingFromLibrary(1L);

            assertNotNull(pending);
            assertEquals(BOOKMARKS_JSON, pending.bookmarksJson());
            assertNotNull(pending.templatePath());
            assertNotEquals("/source/path.docx", pending.templatePath());
        }
    }

    /* ===================== findAll ===================== */

    @Nested
    @DisplayName("findAll 查询所有模板")
    class FindAll {

        @Test
        @DisplayName("返回所有模板（按创建时间倒序）")
        void shouldReturnAllTemplates() {
            Template t1 = new Template(1L, "模板1", "admin", "/p1.docx", "{}", 0, null);
            Template t2 = new Template(2L, "模板2", "admin", "/p2.docx", "{}", 1, null);
            when(templateRepository.findAll(any(org.springframework.data.domain.Sort.class)))
                    .thenReturn(Arrays.asList(t2, t1));

            List<Template> result = templateService.findAll();

            assertEquals(2, result.size());
            assertEquals("模板2", result.get(0).getName());
        }
    }
}
