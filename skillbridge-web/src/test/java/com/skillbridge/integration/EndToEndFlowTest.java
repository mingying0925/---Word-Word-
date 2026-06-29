package com.skillbridge.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.service.PythonExportClient;
import com.skillbridge.utils.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 端到端集成测试：覆盖"上传模板 → 解析书签 → 确认创建活动 → 学生提交 → 导出 Word"完整链路。
 * <p>
 * 使用 @SpringBootTest 加载完整应用上下文，H2 内存数据库，Mock PythonExportClient
 * （避免依赖真实 Python 微服务），Mock JwtInterceptor（避免 JWT 认证干扰）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class EndToEndFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @MockBean
    private PythonExportClient pythonExportClient;

    @Autowired
    private CryptoUtil cryptoUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 模拟 Python 解析返回的书签 JSON（含基本信息 + 工作经历书签） */
    private static final String BOOKMARKS_JSON = """
            {
              "bookmarks": [
                {"name": "name", "table_index": 0, "row": 0, "col": 1, "type": "text", "options": []},
                {"name": "gender", "table_index": 0, "row": 1, "col": 1, "type": "radio", "options": ["男", "女"]},
                {"name": "work_start_1", "table_index": 0, "row": 5, "col": 1, "type": "text", "options": []},
                {"name": "work_company_1", "table_index": 0, "row": 5, "col": 2, "type": "text", "options": []},
                {"name": "work_position_1", "table_index": 0, "row": 5, "col": 3, "type": "text", "options": []}
              ],
              "tables_structure": [
                {
                  "table_index": 0,
                  "rows": 6,
                  "cols": 4,
                  "cells": [
                    {"row": 0, "col": 0, "text": "姓名", "is_merged": false, "merge_span": null, "bookmark_names": []},
                    {"row": 0, "col": 1, "text": "", "is_merged": false, "merge_span": null, "bookmark_names": ["name"]},
                    {"row": 1, "col": 0, "text": "性别", "is_merged": false, "merge_span": null, "bookmark_names": []},
                    {"row": 1, "col": 1, "text": "", "is_merged": false, "merge_span": null, "bookmark_names": ["gender"]},
                    {"row": 5, "col": 0, "text": "起止时间", "is_merged": false, "merge_span": null, "bookmark_names": []},
                    {"row": 5, "col": 1, "text": "", "is_merged": false, "merge_span": null, "bookmark_names": ["work_start_1"]},
                    {"row": 5, "col": 2, "text": "", "is_merged": false, "merge_span": null, "bookmark_names": ["work_company_1"]},
                    {"row": 5, "col": 3, "text": "", "is_merged": false, "merge_span": null, "bookmark_names": ["work_position_1"]}
                  ]
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        when(jwtInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("完整链路：上传模板 → 确认创建活动 → 学生提交 → 导出 Word")
    void fullFlowFromUploadToExport() throws Exception {
        MockHttpSession session = new MockHttpSession();

        // 1. 模拟 Python 解析书签
        when(pythonExportClient.parseBookmarks(any())).thenReturn(BOOKMARKS_JSON);

        // 2. GET /teacher/create 获取 CSRF Token
        mockMvc.perform(get("/teacher/create").session(session))
                .andExpect(status().isOk());
        String csrfToken = (String) session.getAttribute("csrfToken");
        assertNotNull(csrfToken, "CSRF token should be set in session after GET request");

        // 3. POST /teacher/create 上传模板 → 保存草稿 → 重定向到确认页
        MockMultipartFile templateFile = new MockMultipartFile(
                "templateFile", "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx-content".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/teacher/create")
                        .file(templateFile)
                        .param("name", "集成测试活动")
                        .param("deadline", "2026-12-31T23:59")
                        .param("_csrf", csrfToken)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/teacher/create/confirm?draftId=*"))
                .andReturn();

        // 提取 draftId
        String redirectedUrl = createResult.getResponse().getRedirectedUrl();
        Long draftId = Long.parseLong(redirectedUrl.split("draftId=")[1]);
        assertNotNull(draftId, "Draft ID should be extracted from redirect URL");

        // 4. GET /teacher/create/confirm?draftId=X 确认页
        mockMvc.perform(get("/teacher/create/confirm")
                        .param("draftId", draftId.toString())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/confirm-fields"))
                .andExpect(model().attribute("pendingName", "集成测试活动"))
                .andExpect(model().attribute("draftId", draftId))
                .andExpect(model().attribute("hasWorkExperience", true));

        // 5. POST /teacher/create/confirm 确认保存 → 草稿转正式活动
        mockMvc.perform(post("/teacher/create/confirm")
                        .param("draftId", draftId.toString())
                        .param("_csrf", csrfToken)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/activities"));

        // 6. GET /teacher/activities 验证活动出现在列表中
        mockMvc.perform(get("/teacher/activities").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/activity-list"))
                .andExpect(model().attributeExists("activities"));

        // 7. 模拟 Python 填充 Word
        when(pythonExportClient.fillWord(anyString(), anyMap()))
                .thenReturn("fake-filled-docx".getBytes());

        // 8. 学生提交报名（通过 session 模拟学生登录状态）
        MockHttpSession studentSession = new MockHttpSession();
        studentSession.setAttribute("studentId", "2024001");
        studentSession.setAttribute("idCard", cryptoUtil.encrypt("440301199001011234"));
        studentSession.setAttribute("studentName", "张三");

        // 获取学生 session 的 CSRF Token（/student/login 被 CSRF 过滤器排除，改用根路径）
        mockMvc.perform(get("/").session(studentSession))
                .andExpect(status().isOk());
        String studentCsrf = (String) studentSession.getAttribute("csrfToken");

        // 9. POST /student/activity/{id}/submit 提交报名
        mockMvc.perform(post("/student/activity/{id}/submit", draftId)
                        .param("_csrf", studentCsrf)
                        .param("name", "张三")
                        .param("gender", "男")
                        .param("workExp[0].startDate", "2020-01-01")
                        .param("workExp[0].endDate", "2021-12-31")
                        .param("workExp[0].companyName", "测试公司")
                        .param("workExp[0].position", "测试工程师")
                        .session(studentSession))
                .andExpect(status().is3xxRedirection());

        // 10. GET /teacher/activity/{id}/submissions 验证提交记录
        mockMvc.perform(get("/teacher/activity/{id}/submissions", draftId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("teacher/submissions"))
                .andExpect(model().attributeExists("activity", "submissions"));
    }

    @Test
    @DisplayName("取消创建草稿时删除草稿记录")
    void cancelCreateShouldDeleteDraft() throws Exception {
        MockHttpSession session = new MockHttpSession();

        when(pythonExportClient.parseBookmarks(any())).thenReturn(BOOKMARKS_JSON);

        // 获取 CSRF Token
        mockMvc.perform(get("/teacher/create").session(session));
        String csrfToken = (String) session.getAttribute("csrfToken");

        // 上传模板创建草稿
        MockMultipartFile templateFile = new MockMultipartFile(
                "templateFile", "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx-content".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/teacher/create")
                        .file(templateFile)
                        .param("name", "待取消活动")
                        .param("_csrf", csrfToken)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectedUrl = createResult.getResponse().getRedirectedUrl();
        Long draftId = Long.parseLong(redirectedUrl.split("draftId=")[1]);

        // 取消创建
        mockMvc.perform(post("/teacher/create/cancel")
                        .param("draftId", draftId.toString())
                        .param("_csrf", csrfToken)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/create"));

        // 确认页应重定向回创建页（草稿已删除）
        mockMvc.perform(get("/teacher/create/confirm")
                        .param("draftId", draftId.toString())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/teacher/create"));
    }

    @Test
    @DisplayName("工作经历书签映射：workExp[i].* → work_*_{i+1}")
    void workExperienceBookmarkMapping() throws Exception {
        MockHttpSession session = new MockHttpSession();

        when(pythonExportClient.parseBookmarks(any())).thenReturn(BOOKMARKS_JSON);
        when(pythonExportClient.fillWord(anyString(), anyMap()))
                .thenAnswer(invocation -> {
                    Map<String, String> data = invocation.getArgument(1);
                    // 验证工作经历字段已正确映射为书签名
                    assertTrue(data.containsKey("work_start_1"), "work_start_1 should be mapped");
                    assertTrue(data.containsKey("work_company_1"), "work_company_1 should be mapped");
                    assertTrue(data.containsKey("work_position_1"), "work_position_1 should be mapped");
                    assertFalse(data.containsKey("workExp[0].startDate"), "workExp[0].startDate should be removed");
                    assertEquals("2020-01-01 - 2021-12-31", data.get("work_start_1"));
                    assertEquals("测试公司", data.get("work_company_1"));
                    assertEquals("测试工程师", data.get("work_position_1"));
                    return "fake-docx".getBytes();
                });

        // 创建活动
        mockMvc.perform(get("/teacher/create").session(session));
        String csrfToken = (String) session.getAttribute("csrfToken");

        MockMultipartFile templateFile = new MockMultipartFile(
                "templateFile", "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx-content".getBytes());

        MvcResult createResult = mockMvc.perform(multipart("/teacher/create")
                        .file(templateFile)
                        .param("name", "映射测试活动")
                        .param("_csrf", csrfToken)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        Long draftId = Long.parseLong(createResult.getResponse().getRedirectedUrl().split("draftId=")[1]);

        // 确认创建
        mockMvc.perform(post("/teacher/create/confirm")
                        .param("draftId", draftId.toString())
                        .param("_csrf", csrfToken)
                        .session(session))
                .andExpect(status().is3xxRedirection());

        // 学生提交（含工作经历）
        MockHttpSession studentSession = new MockHttpSession();
        studentSession.setAttribute("studentId", "2024002");
        studentSession.setAttribute("idCard", cryptoUtil.encrypt("440301199002022345"));
        studentSession.setAttribute("studentName", "李四");

        mockMvc.perform(get("/").session(studentSession));
        String studentCsrf = (String) studentSession.getAttribute("csrfToken");

        mockMvc.perform(post("/student/activity/{id}/submit", draftId)
                        .param("_csrf", studentCsrf)
                        .param("name", "李四")
                        .param("workExp[0].startDate", "2020-01-01")
                        .param("workExp[0].endDate", "2021-12-31")
                        .param("workExp[0].companyName", "测试公司")
                        .param("workExp[0].position", "测试工程师")
                        .session(studentSession))
                .andExpect(status().is3xxRedirection());
    }
}
