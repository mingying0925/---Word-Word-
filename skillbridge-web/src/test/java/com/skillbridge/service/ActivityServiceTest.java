package com.skillbridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.model.Activity;
import com.skillbridge.model.Submission;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ActivityService 单元测试。
 * 覆盖：创建活动、提交报名、重复提交校验、截止校验、结构解析、导出 Word。
 */
class ActivityServiceTest {

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private SubmissionRepository submissionRepository;
    @Mock
    private PythonExportClient pythonExportClient;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ActivityService activityService;

    @TempDir
    Path tempDir;

    @Spy
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 注入 uploadDir 配置
        ReflectionTestUtils.setField(activityService, "uploadDir", tempDir.toString());
        // 默认行为：fileStorageService.store 返回临时路径，resolveLocalPath 返回对应 Path
        Path storedPath = tempDir.resolve("template.docx");
        when(fileStorageService.store(any())).thenReturn(storedPath.toString());
        when(fileStorageService.resolveLocalPath(anyString())).thenReturn(storedPath);
    }

    /* ===================== createActivity ===================== */

    @Nested
    @DisplayName("createActivity 创建活动")
    class CreateActivity {

        @Test
        @DisplayName("文件为空时抛出业务异常")
        void shouldThrowWhenFileIsEmpty() {
            MultipartFile emptyFile = new MockMultipartFile("templateFile", "empty.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[0]);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.createActivity("测试活动", null, emptyFile, "admin"));
            assertTrue(ex.getMessage().contains("请上传"));
        }

        @Test
        @DisplayName("非 .docx 文件抛出业务异常")
        void shouldThrowWhenNotDocx() {
            MultipartFile file = new MockMultipartFile("templateFile", "template.txt",
                    "text/plain", "hello".getBytes());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.createActivity("测试活动", null, file, "admin"));
            assertTrue(ex.getMessage().contains(".docx"));
        }

        @Test
        @DisplayName("Python 解析失败时抛出异常并清理已保存文件")
        void shouldCleanUpFileWhenParseFails() throws Exception {
            MockMultipartFile file = new MockMultipartFile("templateFile", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake-docx".getBytes());
            when(pythonExportClient.parseBookmarks(any())).thenThrow(new BusinessException("Python 解析失败"));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.createActivity("测试活动", null, file, "admin"));
            assertTrue(ex.getMessage().contains("Python 解析失败"));
            // 验证活动未被保存
            verify(activityRepository, never()).save(any());
            // 验证文件已被清理（fileStorageService.delete 被调用）
            verify(fileStorageService).delete(anyString());
        }

        @Test
        @DisplayName("成功创建活动并保存书签 JSON")
        void shouldCreateActivitySuccessfully() {
            MockMultipartFile file = new MockMultipartFile("templateFile", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake-docx".getBytes());
            String bookmarksJson = "{\"bookmarks\":[],\"tables_structure\":[]}";
            when(pythonExportClient.parseBookmarks(any())).thenReturn(bookmarksJson);
            when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> {
                Activity a = inv.getArgument(0);
                a.setId(1L);
                return a;
            });

            Activity result = activityService.createActivity("测试活动", null, file, "admin");

            assertNotNull(result);
            assertEquals("测试活动", result.getName());
            assertEquals(bookmarksJson, result.getBookmarksJson());
            assertEquals(0, result.getStatus());
            assertNotNull(result.getTemplatePath());
            verify(activityRepository).save(any(Activity.class));
        }
    }

    /* ===================== submitRegistration ===================== */

    @Nested
    @DisplayName("submitRegistration 学生提交报名")
    class SubmitRegistration {

        @Test
        @DisplayName("学号为空时抛出异常")
        void shouldThrowWhenStudentIdBlank() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.submitRegistration(1L, "", "440301199001011234", new HashMap<>()));
            assertTrue(ex.getMessage().contains("学号"));
        }

        @Test
        @DisplayName("身份证号为空时抛出异常")
        void shouldThrowWhenIdCardBlank() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.submitRegistration(1L, "2024001", "", new HashMap<>()));
            assertTrue(ex.getMessage().contains("身份证"));
        }

        @Test
        @DisplayName("活动不存在时抛出异常")
        void shouldThrowWhenActivityNotFound() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.submitRegistration(99L, "2024001", "440301199001011234", new HashMap<>()));
            assertTrue(ex.getMessage().contains("活动不存在"));
        }

        @Test
        @DisplayName("活动已截止(status=1)时抛出异常")
        void shouldThrowWhenActivityClosed() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(1);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.submitRegistration(1L, "2024001", "440301199001011234", new HashMap<>()));
            assertTrue(ex.getMessage().contains("已截止"));
        }

        @Test
        @DisplayName("超过截止时间时抛出异常")
        void shouldThrowWhenPastDeadline() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(0);
            activity.setDeadline(LocalDateTime.now().minusDays(1));
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.submitRegistration(1L, "2024001", "440301199001011234", new HashMap<>()));
            assertTrue(ex.getMessage().contains("截止"));
        }

        @Test
        @DisplayName("重复提交时抛出异常")
        void shouldThrowWhenDuplicateSubmission() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(0);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(submissionRepository.findByActivityIdAndStudentIdAndIdCard(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.of(new Submission()));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.submitRegistration(1L, "2024001", "440301199001011234", new HashMap<>()));
            assertTrue(ex.getMessage().contains("重复提交"));
        }

        @Test
        @DisplayName("成功提交报名")
        void shouldSubmitSuccessfully() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(0);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(submissionRepository.findByActivityIdAndStudentIdAndIdCard(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.empty());
            when(submissionRepository.save(any(Submission.class))).thenAnswer(inv -> {
                Submission s = inv.getArgument(0);
                s.setId(1L);
                return s;
            });

            Map<String, String> formData = new HashMap<>();
            formData.put("name", "张三");

            Submission result = activityService.submitRegistration(1L, "2024001", "440301199001011234", formData);

            assertNotNull(result);
            assertEquals("2024001", result.getStudentId());
            assertEquals("440301199001011234", result.getIdCard());
            assertNotNull(result.getFormDataJson());
            assertTrue(result.getFormDataJson().contains("张三"));
            verify(submissionRepository).save(any(Submission.class));
        }
    }

    /* ===================== closeActivity ===================== */

    @Nested
    @DisplayName("closeActivity 截止活动")
    class CloseActivity {

        @Test
        @DisplayName("活动存在且未截止时改为截止状态")
        void shouldCloseActivity() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(0);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

            Activity result = activityService.closeActivity(1L, "admin");

            assertEquals(1, result.getStatus());
            verify(activityRepository).save(activity);
        }

        @Test
        @DisplayName("活动不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.closeActivity(99L, "admin"));
            assertTrue(ex.getMessage().contains("活动不存在"));
        }

        @Test
        @DisplayName("已截止活动重复截止抛出异常")
        void shouldThrowWhenAlreadyClosed() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(1);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.closeActivity(1L, "admin"));
            assertTrue(ex.getMessage().contains("已截止"));
        }
    }

    /* ===================== deleteActivity ===================== */

    @Nested
    @DisplayName("deleteActivity 删除活动")
    class DeleteActivity {

        @Test
        @DisplayName("活动不存在时抛出异常")
        void shouldThrowWhenNotFound() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.deleteActivity(99L, "admin"));
            assertTrue(ex.getMessage().contains("活动不存在"));
        }

        @Test
        @DisplayName("删除活动级联删除提交记录和模板文件")
        void shouldDeleteCascade() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath(tempDir.resolve("template.docx").toString());
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            when(submissionRepository.findByActivityId(1L)).thenReturn(Collections.emptyList());

            activityService.deleteActivity(1L, "admin");

            verify(submissionRepository).deleteAll(Collections.emptyList());
            verify(activityRepository).delete(activity);
        }

        @Test
        @DisplayName("删除活动时清理关联提交记录")
        void shouldDeleteRelatedSubmissions() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath("/tmp/template.docx");
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            Submission sub1 = new Submission();
            sub1.setId(1L);
            Submission sub2 = new Submission();
            sub2.setId(2L);
            when(submissionRepository.findByActivityId(1L)).thenReturn(List.of(sub1, sub2));

            activityService.deleteActivity(1L, "admin");

            verify(submissionRepository).deleteAll(List.of(sub1, sub2));
            verify(activityRepository).delete(activity);
        }
    }

    /* ===================== checkLogin ===================== */

    @Test
    @DisplayName("checkLogin 返回已存在的提交记录")
    void checkLoginShouldReturnExistingSubmission() {
        Submission existing = new Submission();
        existing.setId(1L);
        when(submissionRepository.findByActivityIdAndStudentIdAndIdCard(1L, "2024001", "440301"))
                .thenReturn(Optional.of(existing));

        Optional<Submission> result = activityService.checkLogin(1L, "2024001", "440301");
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    /* ===================== getActivityStructure / parseStructure ===================== */

    @Nested
    @DisplayName("getActivityStructure 解析表格结构")
    class GetActivityStructure {

        @Test
        @DisplayName("活动不存在时抛出异常")
        void shouldThrowWhenActivityNotFound() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.getActivityStructure(99L));
            assertTrue(ex.getMessage().contains("活动不存在"));
        }

        @Test
        @DisplayName("书签 JSON 为空时返回空列表")
        void shouldReturnEmptyWhenNoBookmarks() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setBookmarksJson("");
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            Map<String, Object> result = activityService.getActivityStructure(1L);
            assertNotNull(result.get("activity"));
            assertTrue(((List<?>) result.get("tablesStructure")).isEmpty());
            assertTrue(((List<?>) result.get("bookmarks")).isEmpty());
        }

        @Test
        @DisplayName("正确解析含合并单元格的表格结构")
        void shouldParseStructureWithMergedCells() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            // 构造一个含 gridSpan 合并的表格结构（HashMap 允许 null 值）
            Map<String, Object> cell0 = new HashMap<>();
            cell0.put("row", 0); cell0.put("col", 0); cell0.put("text", "姓名");
            cell0.put("is_merged", false); cell0.put("merge_span", null);
            cell0.put("bookmark_names", Collections.emptyList());
            Map<String, Object> cell1 = new HashMap<>();
            cell1.put("row", 0); cell1.put("col", 1); cell1.put("text", "");
            cell1.put("is_merged", false); cell1.put("merge_span", null);
            cell1.put("bookmark_names", List.of("姓名"));
            Map<String, Object> bookmark = new HashMap<>();
            bookmark.put("name", "姓名"); bookmark.put("table_index", 0);
            bookmark.put("row", 0); bookmark.put("col", 1);
            bookmark.put("type", "text"); bookmark.put("options", Collections.emptyList());
            Map<String, Object> table = new HashMap<>();
            table.put("table_index", 0); table.put("rows", 1); table.put("cols", 2);
            table.put("cells", List.of(cell0, cell1));
            Map<String, Object> root = new HashMap<>();
            root.put("bookmarks", List.of(bookmark));
            root.put("tables_structure", List.of(table));
            String json = objectMapper.writeValueAsString(root);
            activity.setBookmarksJson(json);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            Map<String, Object> result = activityService.getActivityStructure(1L);
            List<?> tables = (List<?>) result.get("tablesStructure");
            assertEquals(1, tables.size());
            @SuppressWarnings("unchecked")
            Map<String, Object> tableResult = (Map<String, Object>) tables.get(0);
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>) tableResult.get("rows");
            assertEquals(1, rows.size());
            assertEquals(2, rows.get(0).size());
            // 第二个单元格是书签单元格
            Map<String, Object> bookmarkCell = rows.get(0).get(1);
            assertTrue((Boolean) bookmarkCell.get("isBookmark"));
            assertEquals("text", bookmarkCell.get("type"));
        }

        @Test
        @DisplayName("性别书签推断为 radio 类型")
        void shouldInferRadioTypeForGender() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            Map<String, Object> cell0 = new HashMap<>();
            cell0.put("row", 0); cell0.put("col", 0); cell0.put("text", "性别");
            cell0.put("is_merged", false); cell0.put("merge_span", null);
            cell0.put("bookmark_names", Collections.emptyList());
            Map<String, Object> cell1 = new HashMap<>();
            cell1.put("row", 0); cell1.put("col", 1); cell1.put("text", "");
            cell1.put("is_merged", false); cell1.put("merge_span", null);
            cell1.put("bookmark_names", List.of("性别"));
            Map<String, Object> bookmark = new HashMap<>();
            bookmark.put("name", "性别"); bookmark.put("table_index", 0);
            bookmark.put("row", 0); bookmark.put("col", 1);
            bookmark.put("type", "radio"); bookmark.put("options", List.of("男", "女"));
            Map<String, Object> table = new HashMap<>();
            table.put("table_index", 0); table.put("rows", 1); table.put("cols", 2);
            table.put("cells", List.of(cell0, cell1));
            Map<String, Object> root = new HashMap<>();
            root.put("bookmarks", List.of(bookmark));
            root.put("tables_structure", List.of(table));
            String json = objectMapper.writeValueAsString(root);
            activity.setBookmarksJson(json);
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            Map<String, Object> result = activityService.getActivityStructure(1L);
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>)
                    ((Map<String, Object>) ((List<?>) result.get("tablesStructure")).get(0)).get("rows");
            Map<String, Object> cell = rows.get(0).get(1);
            assertEquals("radio", cell.get("type"));
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) cell.get("options");
            assertEquals(List.of("男", "女"), options);
        }

        @Test
        @DisplayName("书签元数据(displayName/required/enabled)传递到渲染单元格")
        void shouldPassBookmarkMetadataToRenderedCells() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            Map<String, Object> cell0 = new HashMap<>();
            cell0.put("row", 0); cell0.put("col", 0); cell0.put("text", "姓名");
            cell0.put("is_merged", false); cell0.put("merge_span", null);
            cell0.put("bookmark_names", Collections.emptyList());
            Map<String, Object> cell1 = new HashMap<>();
            cell1.put("row", 0); cell1.put("col", 1); cell1.put("text", "");
            cell1.put("is_merged", false); cell1.put("merge_span", null);
            cell1.put("bookmark_names", List.of("xm"));
            // 书签包含教师编辑的元数据
            Map<String, Object> bookmark = new HashMap<>();
            bookmark.put("name", "xm"); bookmark.put("table_index", 0);
            bookmark.put("row", 0); bookmark.put("col", 1);
            bookmark.put("type", "text"); bookmark.put("options", null);
            bookmark.put("displayName", "学生姓名");
            bookmark.put("required", false);
            bookmark.put("enabled", true);
            Map<String, Object> table = new HashMap<>();
            table.put("table_index", 0); table.put("rows", 1); table.put("cols", 2);
            table.put("cells", List.of(cell0, cell1));
            Map<String, Object> root = new HashMap<>();
            root.put("bookmarks", List.of(bookmark));
            root.put("tables_structure", List.of(table));
            activity.setBookmarksJson(objectMapper.writeValueAsString(root));
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

            Map<String, Object> result = activityService.getActivityStructure(1L);
            @SuppressWarnings("unchecked")
            List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>)
                    ((Map<String, Object>) ((List<?>) result.get("tablesStructure")).get(0)).get("rows");
            Map<String, Object> cell = rows.get(0).get(1);
            // displayName 传递到单元格
            @SuppressWarnings("unchecked")
            List<String> displayNames = (List<String>) cell.get("bookmarkDisplayNames");
            assertEquals("学生姓名", displayNames.get(0));
            // required 传递到单元格（false → 单元格 required 也为 false）
            assertEquals(false, cell.get("required"));
            assertEquals(true, cell.get("enabled"));
        }
    }

    /* ===================== updateDraftFields ===================== */

    @Nested
    @DisplayName("updateDraftFields 更新草稿字段配置")
    class UpdateDraftFields {

        @Test
        @DisplayName("草稿不存在时抛出异常")
        void shouldThrowWhenDraftNotFound() {
            when(activityRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.updateDraftFields(99L, "[]", "admin"));
            assertTrue(ex.getMessage().contains("草稿不存在"));
        }

        @Test
        @DisplayName("非草稿状态活动抛出异常")
        void shouldThrowWhenNotDraft() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setStatus(0); // 报名中，非草稿
            when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
            assertThrows(BusinessException.class,
                    () -> activityService.updateDraftFields(1L, "[]", "admin"));
        }

        @Test
        @DisplayName("fieldConfigs 为空时直接返回草稿不修改")
        void shouldReturnDraftWhenConfigsEmpty() {
            Activity draft = new Activity();
            draft.setId(1L);
            draft.setStatus(2);
            draft.setBookmarksJson("{\"bookmarks\":[]}");
            when(activityRepository.findById(1L)).thenReturn(Optional.of(draft));

            Activity result = activityService.updateDraftFields(1L, "", "admin");
            assertSame(draft, result);
            verify(activityRepository, never()).save(any());
        }

        @Test
        @DisplayName("成功更新书签的 displayName/required/enabled")
        void shouldUpdateBookmarkMetadata() throws Exception {
            // 构造草稿活动
            Activity draft = new Activity();
            draft.setId(1L);
            draft.setStatus(2);
            Map<String, Object> bookmark = new HashMap<>();
            bookmark.put("name", "xm");
            bookmark.put("type", "text");
            Map<String, Object> root = new HashMap<>();
            root.put("bookmarks", List.of(bookmark));
            root.put("tables_structure", Collections.emptyList());
            draft.setBookmarksJson(objectMapper.writeValueAsString(root));
            when(activityRepository.findById(1L)).thenReturn(Optional.of(draft));
            when(activityRepository.save(any(Activity.class))).thenAnswer(inv -> inv.getArgument(0));

            // 教师提交的字段配置
            String fieldConfigs = "[{\"name\":\"xm\",\"displayName\":\"学生姓名\",\"required\":false,\"enabled\":true}]";
            Activity result = activityService.updateDraftFields(1L, fieldConfigs, "admin");

            assertNotNull(result);
            // 验证 bookmarksJson 已更新
            Map<String, Object> updatedRoot = objectMapper.readValue(result.getBookmarksJson(), Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bookmarks = (List<Map<String, Object>>) updatedRoot.get("bookmarks");
            assertEquals(1, bookmarks.size());
            assertEquals("学生姓名", bookmarks.get(0).get("displayName"));
            assertEquals(false, bookmarks.get(0).get("required"));
            assertEquals(true, bookmarks.get(0).get("enabled"));
        }

        @Test
        @DisplayName("无效 JSON 时抛出业务异常")
        void shouldThrowOnInvalidJson() {
            Activity draft = new Activity();
            draft.setId(1L);
            draft.setStatus(2);
            draft.setBookmarksJson("{\"bookmarks\":[]}");
            when(activityRepository.findById(1L)).thenReturn(Optional.of(draft));

            assertThrows(BusinessException.class,
                    () -> activityService.updateDraftFields(1L, "invalid-json", "admin"));
        }
    }

    /* ===================== exportWord ===================== */

    @Nested
    @DisplayName("exportWord 导出 Word")
    class ExportWord {

        @Test
        @DisplayName("提交记录不存在时抛出异常")
        void shouldThrowWhenSubmissionNotFound() {
            when(submissionRepository.findById(99L)).thenReturn(Optional.empty());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.exportWord(99L));
            assertTrue(ex.getMessage().contains("报名记录不存在"));
        }

        @Test
        @DisplayName("未关联活动时抛出异常")
        void shouldThrowWhenNoActivity() {
            Submission sub = new Submission();
            sub.setId(1L);
            sub.setActivity(null);
            when(submissionRepository.findById(1L)).thenReturn(Optional.of(sub));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.exportWord(1L));
            assertTrue(ex.getMessage().contains("未关联活动"));
        }

        @Test
        @DisplayName("模板路径为空时抛出异常")
        void shouldThrowWhenNoTemplatePath() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath(null);
            Submission sub = new Submission();
            sub.setId(1L);
            sub.setActivity(activity);
            when(submissionRepository.findById(1L)).thenReturn(Optional.of(sub));

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> activityService.exportWord(1L));
            assertTrue(ex.getMessage().contains("模板"));
        }

        @Test
        @DisplayName("成功导出 Word 字节流")
        void shouldExportWordSuccessfully() {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setTemplatePath("/tmp/template.docx");
            Submission sub = new Submission();
            sub.setId(1L);
            sub.setActivity(activity);
            sub.setFormDataJson("{\"name\":\"张三\"}");
            when(submissionRepository.findById(1L)).thenReturn(Optional.of(sub));
            when(pythonExportClient.fillWord(eq("/tmp/template.docx"), anyMap()))
                    .thenReturn("fake-docx".getBytes());

            byte[] result = activityService.exportWord(1L);
            assertNotNull(result);
            assertEquals("fake-docx", new String(result));
            verify(pythonExportClient).fillWord(eq("/tmp/template.docx"), anyMap());
        }
    }
}
