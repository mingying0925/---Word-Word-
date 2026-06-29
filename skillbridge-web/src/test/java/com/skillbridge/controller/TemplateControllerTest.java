package com.skillbridge.controller;

import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.model.Template;
import com.skillbridge.service.ActivityService;
import com.skillbridge.service.AuditLogService;
import com.skillbridge.service.BusinessException;
import com.skillbridge.service.TemplateService;
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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TemplateController 集成测试。
 * 使用 @WebMvcTest 隔离 Web 层，Mock TemplateService。
 */
@WebMvcTest(TemplateController.class)
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TemplateService templateService;

    @MockBean
    private AuditLogService auditLogService;

    @MockBean
    private ActivityService activityService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CookieHelper cookieHelper;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @BeforeEach
    void passThroughJwt() throws Exception {
        when(jwtInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-token");
        when(cookieHelper.getCookieName()).thenReturn("skillbridge_token");
    }

    /* ===================== 列表页 ===================== */

    @Nested
    @DisplayName("GET /teacher/templates 模板库列表页")
    class ListTemplates {

        @Test
        @DisplayName("返回模板库列表页")
        void shouldReturnTemplateLibraryPage() throws Exception {
            Template t1 = new Template(1L, "模板1", "admin", "/p1.docx", "{}", 2, LocalDateTime.now());
            Template t2 = new Template(2L, "模板2", "admin", "/p2.docx", "{}", 3, LocalDateTime.now());
            when(templateService.findByOwner(anyString())).thenReturn(Arrays.asList(t2, t1));

            mockMvc.perform(get("/teacher/templates"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("teacher/template-library"))
                    .andExpect(model().attributeExists("templates"));
        }

        @Test
        @DisplayName("模板库为空时也能正常渲染")
        void shouldRenderEmptyList() throws Exception {
            when(templateService.findByOwner(anyString())).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/teacher/templates"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("teacher/template-library"));
        }
    }

    /* ===================== 从活动保存模板 ===================== */

    @Nested
    @DisplayName("POST /teacher/templates/save-from-activity")
    class SaveFromActivity {

        @Test
        @DisplayName("成功保存后重定向到模板库")
        void shouldRedirectOnSuccess() throws Exception {
            Template template = new Template(10L, "测试模板", "admin", "/p.docx", "{}", 2, LocalDateTime.now());
            when(templateService.saveFromActivity(eq(1L), eq("测试模板"), anyString())).thenReturn(template);

            mockMvc.perform(post("/teacher/templates/save-from-activity")
                            .param("activityId", "1")
                            .param("name", "测试模板"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/templates"));

            verify(auditLogService).record(anyString(), eq("teacher"), eq("SAVE_TEMPLATE"),
                    eq("template"), eq("10"), anyString(), anyString());
        }

        @Test
        @DisplayName("业务异常时重定向回模板库并带错误信息")
        void shouldRedirectOnError() throws Exception {
            when(templateService.saveFromActivity(eq(99L), anyString(), anyString()))
                    .thenThrow(new BusinessException("活动不存在: 99"));

            mockMvc.perform(post("/teacher/templates/save-from-activity")
                            .param("activityId", "99")
                            .param("name", "测试模板"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/templates"));
        }
    }

    /* ===================== 上传新模板 ===================== */

    @Nested
    @DisplayName("POST /teacher/templates/upload")
    class UploadTemplate {

        @Test
        @DisplayName("成功上传后重定向到模板库")
        void shouldRedirectOnSuccess() throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake".getBytes());
            Template template = new Template(20L, "上传模板", "admin", "/p.docx", "{}", 2, LocalDateTime.now());
            when(templateService.saveFromUpload(eq("上传模板"), any(), anyString())).thenReturn(template);

            mockMvc.perform(multipart("/teacher/templates/upload")
                            .file(file)
                            .param("name", "上传模板"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/templates"));

            verify(auditLogService).record(anyString(), eq("teacher"), eq("UPLOAD_TEMPLATE"),
                    eq("template"), eq("20"), anyString(), anyString());
        }

        @Test
        @DisplayName("业务异常时重定向回模板库")
        void shouldRedirectOnError() throws Exception {
            MockMultipartFile file = new MockMultipartFile("file", "template.docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "fake".getBytes());
            when(templateService.saveFromUpload(anyString(), any(), anyString()))
                    .thenThrow(new BusinessException("仅支持 .docx 模板文件"));

            mockMvc.perform(multipart("/teacher/templates/upload")
                            .file(file)
                            .param("name", "上传模板"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/templates"));
        }
    }

    /* ===================== 删除模板 ===================== */

    @Nested
    @DisplayName("POST /teacher/templates/delete")
    class DeleteTemplate {

        @Test
        @DisplayName("成功删除后重定向到模板库")
        void shouldRedirectOnSuccess() throws Exception {
            doNothing().when(templateService).delete(eq(1L), anyString());

            mockMvc.perform(post("/teacher/templates/delete")
                            .param("id", "1"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/templates"));

            verify(auditLogService).record(anyString(), eq("teacher"), eq("DELETE_TEMPLATE"),
                    eq("template"), eq("1"), anyString(), anyString());
        }

        @Test
        @DisplayName("模板不存在时重定向回模板库")
        void shouldRedirectOnError() throws Exception {
            doThrow(new BusinessException("模板不存在: 99"))
                    .when(templateService).delete(eq(99L), anyString());

            mockMvc.perform(post("/teacher/templates/delete")
                            .param("id", "99"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/teacher/templates"));
        }
    }
}
