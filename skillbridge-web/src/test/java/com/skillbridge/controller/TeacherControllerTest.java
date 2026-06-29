package com.skillbridge.controller;

import com.skillbridge.model.Activity;
import com.skillbridge.model.Submission;
import com.skillbridge.model.Teacher;
import com.skillbridge.service.ActivityService;
import com.skillbridge.service.AsyncExportService;
import com.skillbridge.service.AuditLogService;
import com.skillbridge.service.BusinessException;
import com.skillbridge.service.DashboardService;
import com.skillbridge.service.StudentRosterService;
import com.skillbridge.service.TeacherService;
import com.skillbridge.service.TemplateService;
import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.utils.CookieHelper;
import com.skillbridge.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TeacherController 集成测试。
 * 使用 @WebMvcTest 隔离 Web 层，Mock ActivityService / TeacherService。
 */
@WebMvcTest(TeacherController.class)
class TeacherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;

    @MockBean
    private TeacherService teacherService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private AsyncExportService asyncExportService;

    @MockBean
    private TemplateService templateService;

    @MockBean
    private StudentRosterService rosterService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CookieHelper cookieHelper;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @BeforeEach
    void passThroughJwt() throws Exception {
        // 切片测试不携带 JWT Cookie，让拦截器放行所有请求
        when(jwtInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-token");
        when(cookieHelper.getCookieName()).thenReturn("skillbridge_token");
    }

    /* ===================== 创建活动页面 ===================== */

    @Test
    @DisplayName("GET /teacher/create 返回创建活动页面")
    void shouldReturnCreatePage() throws Exception {
        mockMvc.perform(get("/teacher/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/create-activity"));
    }

    /* ===================== 创建活动 POST（步骤1：上传解析并保存草稿） ===================== */

    @Nested
    @DisplayName("POST /teacher/create 上传并解析模板")
    class CreateActivity {

        @Test
        @DisplayName("成功解析后保存草稿并重定向到确认页（含 draftId）")
        void shouldRedirectToConfirmOnSuccess() throws Exception {
            MockMultipartFile file = new MockMultipartFile("templateFile", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake".getBytes());
            ActivityService.PendingTemplate pending =
                    new ActivityService.PendingTemplate("/tmp/template.docx", "{\"bookmarks\":[],\"tables_structure\":[]}");
            when(activityService.parseTemplate(any(MultipartFile.class)))
                    .thenReturn(pending);
            Activity draft = new Activity();
            draft.setId(42L);
            draft.setName("测试活动");
            draft.setStatus(2);
            when(activityService.saveDraft(eq("测试活动"), isNull(), eq("/tmp/template.docx"), anyString(), anyString()))
                    .thenReturn(draft);

            mockMvc.perform(multipart("/teacher/create")
                            .file(file)
                            .param("name", "测试活动")
                            .param("deadline", ""))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create/confirm?draftId=42"));
        }

        @Test
        @DisplayName("业务异常时重定向回创建页")
        void shouldRedirectBackOnBusinessError() throws Exception {
            MockMultipartFile file = new MockMultipartFile("templateFile", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake".getBytes());
            when(activityService.parseTemplate(any(MultipartFile.class)))
                    .thenThrow(new BusinessException("仅支持 .docx 模板文件"));

            mockMvc.perform(multipart("/teacher/create")
                            .file(file)
                            .param("name", "测试活动")
                            .param("deadline", ""))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create"));
        }

        @Test
        @DisplayName("不传 deadline 参数也能成功解析（PRD: 截止时间可空）")
        void shouldParseWithoutDeadlineParam() throws Exception {
            MockMultipartFile file = new MockMultipartFile("templateFile", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake".getBytes());
            ActivityService.PendingTemplate pending =
                    new ActivityService.PendingTemplate("/tmp/template.docx", "{\"bookmarks\":[],\"tables_structure\":[]}");
            when(activityService.parseTemplate(any(MultipartFile.class)))
                    .thenReturn(pending);
            Activity draft = new Activity();
            draft.setId(7L);
            draft.setName("无截止活动");
            draft.setStatus(2);
            when(activityService.saveDraft(eq("无截止活动"), isNull(), anyString(), anyString(), anyString()))
                    .thenReturn(draft);

            mockMvc.perform(multipart("/teacher/create")
                            .file(file)
                            .param("name", "无截止活动"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create/confirm?draftId=7"));
        }

        @Test
        @DisplayName("携带 templateId 时从模板库创建（不解析上传文件）")
        void shouldCreateFromTemplateLibrary() throws Exception {
            ActivityService.PendingTemplate pending =
                    new ActivityService.PendingTemplate("/tmp/copied.docx", "{\"bookmarks\":[],\"tables_structure\":[]}");
            when(templateService.preparePendingFromLibrary(eq(5L))).thenReturn(pending);
            Activity draft = new Activity();
            draft.setId(88L);
            draft.setName("从模板库创建");
            draft.setStatus(2);
            when(activityService.saveDraft(eq("从模板库创建"), isNull(), eq("/tmp/copied.docx"), anyString(), anyString()))
                    .thenReturn(draft);

            mockMvc.perform(post("/teacher/create")
                            .param("name", "从模板库创建")
                            .param("templateId", "5"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create/confirm?draftId=88"));

            verify(activityService, never()).parseTemplate(any());
        }

        @Test
        @DisplayName("既无 templateId 又无 templateFile 时重定向回创建页")
        void shouldRedirectWhenNoTemplateSource() throws Exception {
            mockMvc.perform(post("/teacher/create")
                            .param("name", "无模板活动"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create"));

            verify(activityService, never()).saveDraft(any(), any(), any(), any(), any());
        }
    }

    /* ===================== 确认字段（步骤2/3，基于 draftId） ===================== */

    @Nested
    @DisplayName("GET/POST /teacher/create/confirm 确认字段")
    class ConfirmFields {

        @Test
        @DisplayName("缺少 draftId 参数时重定向回创建页")
        void shouldRedirectWhenNoDraftId() throws Exception {
            mockMvc.perform(get("/teacher/create/confirm"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("草稿不存在时重定向回创建页")
        void shouldRedirectWhenDraftNotFound() throws Exception {
            when(activityService.getDraftById(eq(99L), anyString()))
                    .thenThrow(new BusinessException("草稿不存在: 99"));

            mockMvc.perform(get("/teacher/create/confirm").param("draftId", "99"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create"));
        }

        @Test
        @DisplayName("有效 draftId 时返回确认页")
        void shouldReturnConfirmPage() throws Exception {
            Activity draft = new Activity();
            draft.setId(1L);
            draft.setName("测试活动");
            draft.setStatus(2);
            draft.setBookmarksJson("{\"bookmarks\":[],\"tables_structure\":[]}");
            when(activityService.getDraftById(eq(1L), anyString())).thenReturn(draft);

            Map<String, Object> structure = new LinkedHashMap<>();
            structure.put("tablesStructure", Collections.emptyList());
            structure.put("bookmarks", Collections.emptyList());
            when(activityService.parseStructureFromJson(anyString())).thenReturn(structure);

            mockMvc.perform(get("/teacher/create/confirm").param("draftId", "1"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("teacher/confirm-fields"))
                    .andExpect(model().attribute("pendingName", "测试活动"))
                    .andExpect(model().attribute("draftId", 1L));
        }

        @Test
        @DisplayName("确认保存后重定向到活动列表")
        void shouldSaveAndRedirectToActivities() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setName("测试活动");
            activity.setStatus(0);
            when(activityService.confirmDraft(eq(1L), anyString())).thenReturn(activity);

            mockMvc.perform(post("/teacher/create/confirm").param("draftId", "1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/activities"));
        }

        @Test
        @DisplayName("携带字段配置时先更新再确认")
        void shouldUpdateFieldsBeforeConfirm() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setName("测试活动");
            activity.setStatus(0);
            when(activityService.confirmDraft(eq(1L), anyString())).thenReturn(activity);

            String configs = "[{\"name\":\"xm\",\"displayName\":\"学生姓名\",\"required\":true,\"enabled\":true}]";
            mockMvc.perform(post("/teacher/create/confirm")
                            .param("draftId", "1")
                            .param("fieldConfigs", configs))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/activities"));

            // 验证先调用 updateDraftFields，再调用 confirmDraft
            verify(activityService).updateDraftFields(eq(1L), eq(configs), anyString());
            verify(activityService).confirmDraft(eq(1L), anyString());
        }

        @Test
        @DisplayName("草稿不存在时确认保存重定向回创建页")
        void shouldRedirectWhenConfirmDraftNotFound() throws Exception {
            when(activityService.confirmDraft(eq(99L), anyString()))
                    .thenThrow(new BusinessException("草稿不存在: 99"));

            mockMvc.perform(post("/teacher/create/confirm").param("draftId", "99"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/create"));
        }
    }

    /* ===================== 活动列表 ===================== */

    @Test
    @DisplayName("GET /teacher/activities 返回活动列表页面")
    void shouldReturnActivityList() throws Exception {
        Activity a1 = new Activity();
        a1.setId(1L);
        a1.setName("活动1");
        a1.setStatus(0);
        a1.setCreatedAt(LocalDateTime.now());
        org.springframework.data.domain.Page<Activity> page =
                new org.springframework.data.domain.PageImpl<>(List.of(a1), org.springframework.data.domain.PageRequest.of(0, 20), 1);
        when(activityService.searchActivitiesByOwner(anyString(), isNull(), isNull(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/teacher/activities"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/activity-list"))
                .andExpect(model().attributeExists("activities"));
    }

    @Test
    @DisplayName("GET /teacher/activities 空列表也能正常渲染")
    void shouldRenderEmptyList() throws Exception {
        org.springframework.data.domain.Page<Activity> emptyPage =
                new org.springframework.data.domain.PageImpl<>(Collections.emptyList(), org.springframework.data.domain.PageRequest.of(0, 20), 0);
        when(activityService.searchActivitiesByOwner(anyString(), isNull(), isNull(), eq(0), eq(20))).thenReturn(emptyPage);

        mockMvc.perform(get("/teacher/activities"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/activity-list"));
    }

    /* ===================== 查看提交列表 ===================== */

    @Test
    @DisplayName("GET /teacher/activity/{id}/submissions 返回提交列表")
    void shouldReturnSubmissions() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("测试活动");
        activity.setStatus(0);
        when(activityService.getActivityById(1L)).thenReturn(Optional.of(activity));
        when(activityService.getSubmissionsByActivityId(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/teacher/activity/1/submissions"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/submissions"))
                .andExpect(model().attributeExists("activity", "submissions"));
    }

    @Test
    @DisplayName("活动不存在时由全局异常处理返回 error 页面")
    void shouldReturnErrorWhenActivityNotFound() throws Exception {
        when(activityService.getActivityById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/teacher/activity/99/submissions"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("common/error"));
    }

    /* ===================== 预览表单 ===================== */

    @Test
    @DisplayName("GET /teacher/activity/{id}/preview 返回预览页面")
    void shouldReturnPreviewPage() throws Exception {
        Map<String, Object> structure = new LinkedHashMap<>();
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("测试活动");
        structure.put("activity", activity);
        structure.put("tablesStructure", Collections.emptyList());
        structure.put("bookmarks", Collections.emptyList());
        when(activityService.getActivityStructure(1L)).thenReturn(structure);

        mockMvc.perform(get("/teacher/activity/1/preview"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/form"))
                .andExpect(model().attribute("preview", true))
                .andExpect(model().attribute("activityId", 1L));
    }

    /* ===================== 导出 Word ===================== */

    @Test
    @DisplayName("GET /teacher/export/{id} 返回 Word 文件下载")
    void shouldExportWord() throws Exception {
        byte[] docxBytes = "fake-docx-content".getBytes();
        when(activityService.exportWord(1L)).thenReturn(docxBytes);
        when(activityService.buildExportFileName(1L)).thenReturn("张三.docx");

        mockMvc.perform(get("/teacher/export/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    @DisplayName("导出时业务异常返回 error 页面")
    void shouldReturnErrorOnExportFailure() throws Exception {
        when(activityService.exportWord(99L)).thenThrow(new BusinessException("报名记录不存在"));

        mockMvc.perform(get("/teacher/export/99"))
                .andExpect(view().name("common/error"));
    }

    /* ===================== 首页重定向 ===================== */

    @Test
    @DisplayName("GET /teacher 重定向到仪表盘")
    void shouldRedirectToActivities() throws Exception {
        mockMvc.perform(get("/teacher"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/dashboard"));
    }

    /* ===================== 教师登录 ===================== */

    @Test
    @DisplayName("GET /teacher/login 返回登录页")
    void shouldReturnTeacherLoginPage() throws Exception {
        mockMvc.perform(get("/teacher/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/login"));
    }

    @Test
    @DisplayName("POST /teacher/login 成功时重定向到仪表盘")
    void shouldLoginTeacherSuccessfully() throws Exception {
        Teacher teacher = new Teacher();
        teacher.setTeacherId("admin");
        teacher.setName("管理员");
        when(teacherService.login("admin", "ChangeMe123!")).thenReturn(Optional.of(teacher));

        mockMvc.perform(post("/teacher/login")
                        .param("teacherId", "admin")
                        .param("password", "ChangeMe123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/dashboard"));

        verify(cookieHelper).writeTokenCookie(any(), eq("mock-token"));
    }

    @Test
    @DisplayName("POST /teacher/login 密码错误时重定向回登录页")
    void shouldFailTeacherLoginWithWrongPassword() throws Exception {
        when(teacherService.login("admin", "wrong")).thenReturn(Optional.empty());

        mockMvc.perform(post("/teacher/login")
                        .param("teacherId", "admin")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/login"));
    }

    @Test
    @DisplayName("POST /teacher/login 工号格式错误时返回提示")
    void shouldFailTeacherLoginWithInvalidId() throws Exception {
        mockMvc.perform(post("/teacher/login")
                        .param("teacherId", "a")
                        .param("password", "ChangeMe123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/login"));
    }

    /* ===================== 截止活动 ===================== */

    @Test
    @DisplayName("POST /teacher/activity/{id}/close 截止活动成功")
    void shouldCloseActivitySuccessfully() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("测试活动");
        activity.setStatus(1);
        when(activityService.closeActivity(eq(1L), anyString())).thenReturn(activity);

        mockMvc.perform(post("/teacher/activity/1/close"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/activities"));

        verify(activityService).closeActivity(eq(1L), anyString());
    }

    @Test
    @DisplayName("截止已截止的活动返回错误")
    void shouldFailCloseAlreadyClosedActivity() throws Exception {
        doThrow(new BusinessException("该活动已截止，无需重复操作"))
                .when(activityService).closeActivity(eq(1L), anyString());

        mockMvc.perform(post("/teacher/activity/1/close"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/activities"));
    }

    /* ===================== 删除活动 ===================== */

    @Test
    @DisplayName("POST /teacher/activity/{id}/delete 删除活动成功")
    void shouldDeleteActivitySuccessfully() throws Exception {
        mockMvc.perform(post("/teacher/activity/1/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/activities"));

        verify(activityService).deleteActivity(eq(1L), anyString());
    }

    /* ===================== 取消创建 ===================== */

    @Test
    @DisplayName("POST /teacher/create/cancel 带 draftId 时删除草稿并重定向")
    void shouldCancelCreateWithDraftId() throws Exception {
        mockMvc.perform(post("/teacher/create/cancel").param("draftId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/create"));

        verify(activityService).deleteDraft(eq(1L), anyString());
    }

    @Test
    @DisplayName("POST /teacher/create/cancel 不带 draftId 时直接重定向")
    void shouldCancelCreateWithoutDraftId() throws Exception {
        mockMvc.perform(post("/teacher/create/cancel"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/create"));

        verify(activityService, never()).deleteDraft(anyLong(), anyString());
    }

    /* ===================== 批量导出 ZIP ===================== */

    @Test
    @DisplayName("GET /teacher/activity/{id}/export/zip 返回 ZIP 下载")
    void shouldExportZip() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("测试活动");
        when(activityService.getActivityById(1L)).thenReturn(Optional.of(activity));

        mockMvc.perform(get("/teacher/activity/1/export/zip"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/zip"));
    }

    @Test
    @DisplayName("活动不存在时返回 JSON 错误")
    void shouldReturnJsonErrorWhenActivityNotFoundForZip() throws Exception {
        when(activityService.getActivityById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/teacher/activity/99/export/zip"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(false));
    }

    /* ===================== 学生名单管理 ===================== */

    @Nested
    @DisplayName("学生名单管理")
    class StudentRoster {

        @Test
        @DisplayName("GET /teacher/activity/{id}/roster 返回名单页面")
        void shouldReturnRosterPage() throws Exception {
            Activity activity = new Activity();
            activity.setId(1L);
            activity.setName("测试活动");
            when(activityService.getActivityById(1L)).thenReturn(Optional.of(activity));
            when(rosterService.getRosterWithSubmissionStatus(1L)).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/teacher/activity/1/roster"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("teacher/roster"))
                    .andExpect(model().attributeExists("activity", "roster", "rosterCount"));
        }

        @Test
        @DisplayName("POST /teacher/activity/{id}/roster/import 成功导入后重定向")
        void shouldImportRosterSuccessfully() throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "fake".getBytes());
            StudentRosterService.ImportResult result = new StudentRosterService.ImportResult(10, 1);
            when(rosterService.importFromExcel(eq(1L), any())).thenReturn(result);

            mockMvc.perform(multipart("/teacher/activity/1/roster/import")
                            .file(file))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/activity/1/roster"));

            verify(auditLogService).record(anyString(), eq("teacher"), eq("IMPORT_ROSTER"),
                    eq("activity"), eq("1"), anyString(), anyString());
        }

        @Test
        @DisplayName("导入失败时重定向回名单页并带错误信息")
        void shouldRedirectOnImportError() throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "fake".getBytes());
            when(rosterService.importFromExcel(eq(1L), any()))
                    .thenThrow(new BusinessException("仅支持 .xlsx 格式"));

            mockMvc.perform(multipart("/teacher/activity/1/roster/import")
                            .file(file))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/activity/1/roster"));
        }

        @Test
        @DisplayName("POST /teacher/activity/{id}/roster/clear 成功清空名单")
        void shouldClearRosterSuccessfully() throws Exception {
            mockMvc.perform(post("/teacher/activity/1/roster/clear"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/activity/1/roster"));

            verify(rosterService).clearRoster(1L);
        }
    }
}
