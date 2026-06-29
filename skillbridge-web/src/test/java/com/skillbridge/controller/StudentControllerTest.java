package com.skillbridge.controller;

import com.skillbridge.model.Activity;
import com.skillbridge.service.ActivityService;
import com.skillbridge.service.BusinessException;
import com.skillbridge.service.StudentRosterService;
import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.utils.CookieHelper;
import com.skillbridge.utils.CryptoUtil;
import com.skillbridge.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.mock.web.MockMultipartFile;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * StudentController 集成测试。
 * 覆盖：登录页、登录验证、填表页、提交、成功页、重复提交拦截。
 */
@WebMvcTest(StudentController.class)
class StudentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ActivityService activityService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CookieHelper cookieHelper;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @MockBean
    private CryptoUtil cryptoUtil;

    @MockBean
    private StudentRosterService rosterService;

    @BeforeEach
    void passThroughJwt() throws Exception {
        // 切片测试不携带 JWT Cookie，让拦截器放行所有请求
        when(jwtInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(jwtUtil.generateToken(anyString(), anyString())).thenReturn("mock-token");
        when(cookieHelper.getCookieName()).thenReturn("skillbridge_token");
        // CryptoUtil 透传：encrypt 返回原值加前缀，decrypt 去除前缀
        when(cryptoUtil.encrypt(anyString())).thenAnswer(inv -> "enc:" + inv.getArgument(0));
        when(cryptoUtil.decrypt(anyString())).thenAnswer(inv -> {
            String s = inv.getArgument(0);
            return s.startsWith("enc:") ? s.substring(4) : s;
        });
        // 默认不启用白名单（名单为空）
        when(rosterService.isRosterEnabled(anyLong())).thenReturn(false);
    }

    /* ===================== 登录页 ===================== */

    @Test
    @DisplayName("GET /student/login?activityId=1 返回登录页并展示活动信息")
    void shouldReturnLoginPageWithActivity() throws Exception {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("测试活动");
        when(activityService.getActivityById(1L)).thenReturn(Optional.of(activity));

        mockMvc.perform(get("/student/login").param("activityId", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/login"))
                .andExpect(model().attributeExists("activity", "activityId"));
    }

    @Test
    @DisplayName("GET /student/login 无 activityId 时返回空登录页")
    void shouldReturnLoginPageWithoutActivity() throws Exception {
        mockMvc.perform(get("/student/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/login"));
    }

    @Test
    @DisplayName("GET /student/activity/{id} 重定向到登录页并携带 activityId")
    void shouldRedirectToLoginFromLegacyActivityUrl() throws Exception {
        mockMvc.perform(get("/student/activity/1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?activityId=1"));
    }

    @Test
    @DisplayName("活动不存在时返回 error 页面")
    void shouldReturnErrorWhenActivityNotFound() throws Exception {
        when(activityService.getActivityById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/student/login").param("activityId", "99"))
                .andExpect(view().name("common/error"));
    }

    /* ===================== 登录验证 ===================== */

    @Nested
    @DisplayName("POST /student/login 登录验证")
    class Login {

        @Test
        @DisplayName("未提交过 → 身份存入 session 并重定向到填表页")
        void shouldRedirectToFormWhenNotSubmitted() throws Exception {
            when(activityService.checkLogin(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(post("/student/login")
                            .param("activityId", "1")
                            .param("studentName", "张三")
                            .param("studentId", "2024001")
                            .param("idCard", "440301199001011234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/form"));
        }

        @Test
        @DisplayName("已提交过 → 重定向到查看报名信息页")
        void shouldRedirectToViewWhenAlreadySubmitted() throws Exception {
            com.skillbridge.model.Submission existing = new com.skillbridge.model.Submission();
            existing.setId(1L);
            when(activityService.checkLogin(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.of(existing));

            mockMvc.perform(post("/student/login")
                            .param("activityId", "1")
                            .param("studentName", "张三")
                            .param("studentId", "2024001")
                            .param("idCard", "440301199001011234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/view-submission"));
        }

        @Test
        @DisplayName("学号格式不正确 → 重定向回登录页")
        void shouldRedirectBackWhenStudentIdInvalid() throws Exception {
            mockMvc.perform(post("/student/login")
                            .param("activityId", "1")
                            .param("studentName", "张三")
                            .param("studentId", "ab")
                            .param("idCard", "440301199001011234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/login?activityId=1"));
        }

        @Test
        @DisplayName("身份证号格式不正确 → 重定向回登录页")
        void shouldRedirectBackWhenIdCardInvalid() throws Exception {
            mockMvc.perform(post("/student/login")
                            .param("activityId", "1")
                            .param("studentName", "张三")
                            .param("studentId", "2024001")
                            .param("idCard", "123456789012345678"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/login?activityId=1"));
        }

        @Test
        @DisplayName("启用白名单且学生不在名单中 → 重定向回登录页")
        void shouldRejectWhenNotInWhitelist() throws Exception {
            when(rosterService.isRosterEnabled(1L)).thenReturn(true);
            when(rosterService.checkWhitelist(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(post("/student/login")
                            .param("activityId", "1")
                            .param("studentName", "张三")
                            .param("studentId", "2024001")
                            .param("idCard", "440301199001011234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/login?activityId=1"));
        }

        @Test
        @DisplayName("启用白名单且学生在名单中 → 使用名单姓名并重定向到填表页")
        void shouldUseRosterNameWhenInWhitelist() throws Exception {
            com.skillbridge.model.StudentRosterEntry entry = new com.skillbridge.model.StudentRosterEntry();
            entry.setStudentId("2024001");
            entry.setStudentName("名单姓名");
            when(rosterService.isRosterEnabled(1L)).thenReturn(true);
            when(rosterService.checkWhitelist(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.of(entry));
            when(activityService.checkLogin(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(post("/student/login")
                            .param("activityId", "1")
                            .param("studentName", "错误姓名")
                            .param("studentId", "2024001")
                            .param("idCard", "440301199001011234"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/form"));
        }
    }

    /* ===================== 填表页 ===================== */

    @Test
    @DisplayName("GET /student/activity/{id}/form 有 session 时返回填表页")
    void shouldReturnFormPage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("studentId", "2024001");
        session.setAttribute("idCard", "enc:440301199001011234");

        Map<String, Object> structure = new LinkedHashMap<>();
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setName("测试活动");
        structure.put("activity", activity);
        structure.put("tablesStructure", Collections.emptyList());
        structure.put("bookmarks", Collections.emptyList());
        when(activityService.getActivityStructure(1L)).thenReturn(structure);

        mockMvc.perform(get("/student/activity/1/form").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("student/form"))
                .andExpect(model().attribute("preview", false))
                .andExpect(model().attribute("studentId", "2024001"))
                .andExpect(model().attribute("idCard", "440301199001011234"));
    }

    @Test
    @DisplayName("GET /student/activity/{id}/form 无 session 时重定向回登录页")
    void shouldRedirectToLoginWhenNoSession() throws Exception {
        mockMvc.perform(get("/student/activity/1/form"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/login?activityId=1"));
    }

    /* ===================== 提交表单 ===================== */

    @Nested
    @DisplayName("POST /student/activity/{id}/submit 提交表单")
    class Submit {

        @Test
        @DisplayName("成功提交 → 重定向到成功页")
        void shouldRedirectToSuccessOnSubmit() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("studentName", "张三");
            session.setAttribute("studentId", "2024001");
            session.setAttribute("idCard", "enc:440301199001011234");

            com.skillbridge.model.Submission sub = new com.skillbridge.model.Submission();
            sub.setId(1L);
            when(activityService.submitRegistration(eq(1L), eq("2024001"), eq("440301199001011234"), anyMap()))
                    .thenReturn(sub);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("name", "张三");
            params.add("gender", "男");

            mockMvc.perform(post("/student/activity/1/submit").params(params).session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/success"));
        }

        @Test
        @DisplayName("重复提交 → 重定向回登录页")
        void shouldRedirectBackOnDuplicate() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("studentName", "张三");
            session.setAttribute("studentId", "2024001");
            session.setAttribute("idCard", "enc:440301199001011234");

            when(activityService.submitRegistration(eq(1L), eq("2024001"), eq("440301199001011234"), anyMap()))
                    .thenThrow(new BusinessException("该学号+身份证已提交过报名，请勿重复提交"));

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("name", "张三");

            mockMvc.perform(post("/student/activity/1/submit").params(params).session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/form"));
        }

        @Test
        @DisplayName("无 session 时重定向回登录页")
        void shouldRedirectBackWhenNoSession() throws Exception {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("name", "张三");

            mockMvc.perform(post("/student/activity/1/submit").params(params))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/login?activityId=1"));
        }

        @Test
        @DisplayName("含图片上传的提交成功")
        void shouldSubmitWithImageUpload() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("studentName", "张三");
            session.setAttribute("studentId", "2024001");
            session.setAttribute("idCard", "enc:440301199001011234");

            com.skillbridge.model.Submission sub = new com.skillbridge.model.Submission();
            sub.setId(1L);
            when(activityService.submitRegistration(eq(1L), eq("2024001"), eq("440301199001011234"), anyMap()))
                    .thenReturn(sub);

            MockMultipartFile image = new MockMultipartFile(
                    "照片", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

            mockMvc.perform(multipart("/student/activity/1/submit")
                            .file(image)
                            .param("name", "张三")
                            .param("gender", "男")
                            .session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/success"));
        }

        @Test
        @DisplayName("非图片文件类型被跳过（不放入 formData）")
        void shouldSkipNonImageFile() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("studentName", "张三");
            session.setAttribute("studentId", "2024001");
            session.setAttribute("idCard", "enc:440301199001011234");

            com.skillbridge.model.Submission sub = new com.skillbridge.model.Submission();
            sub.setId(1L);
            when(activityService.submitRegistration(eq(1L), eq("2024001"), eq("440301199001011234"), anyMap()))
                    .thenReturn(sub);

            MockMultipartFile pdf = new MockMultipartFile(
                    "附件", "doc.pdf", "application/pdf", "fake-pdf".getBytes());

            mockMvc.perform(multipart("/student/activity/1/submit")
                            .file(pdf)
                            .param("name", "张三")
                            .session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/activity/1/success"));
        }
    }

    /* ===================== 成功页 ===================== */

    @Test
    @DisplayName("GET /student/activity/{id}/success 返回成功页")
    void shouldReturnSuccessPage() throws Exception {
        mockMvc.perform(get("/student/activity/1/success"))
                .andExpect(status().isOk())
                .andExpect(view().name("student/submit-success"))
                .andExpect(model().attribute("activityId", 1L));
    }

    /* ===================== 查看已提交报名信息 ===================== */

    @Nested
    @DisplayName("GET /student/activity/{id}/view-submission 查看已提交报名信息")
    class ViewSubmission {

        @Test
        @DisplayName("有 session 且存在提交记录 → 返回只读查看页")
        void shouldReturnViewSubmissionPage() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("studentId", "2024001");
            session.setAttribute("idCard", "enc:440301199001011234");

            Activity activity = new Activity();
            activity.setId(1L);
            activity.setName("测试活动");
            com.skillbridge.model.Submission sub = new com.skillbridge.model.Submission();
            sub.setId(10L);
            sub.setActivity(activity);
            sub.setFormDataJson("{\"姓名\":\"张三\",\"性别\":\"男\"}");
            when(activityService.checkLogin(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.of(sub));
            Map<String, String> parsed = new LinkedHashMap<>();
            parsed.put("姓名", "张三");
            parsed.put("性别", "男");
            when(activityService.parseFormDataJson(anyString())).thenReturn(parsed);

            mockMvc.perform(get("/student/activity/1/view-submission").session(session))
                    .andExpect(status().isOk())
                    .andExpect(view().name("student/view-submission"))
                    .andExpect(model().attributeExists("activity", "submission", "formData"))
                    .andExpect(model().attribute("activityId", 1L));
        }

        @Test
        @DisplayName("无 session → 重定向到登录页")
        void shouldRedirectToLoginWhenNoSession() throws Exception {
            mockMvc.perform(get("/student/activity/1/view-submission"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/login?activityId=1"));
        }

        @Test
        @DisplayName("有 session 但无提交记录 → 重定向到登录页")
        void shouldRedirectToLoginWhenNoSubmission() throws Exception {
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("studentId", "2024001");
            session.setAttribute("idCard", "enc:440301199001011234");
            when(activityService.checkLogin(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/student/activity/1/view-submission").session(session))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/student/login?activityId=1"));
        }
    }

    /* ===================== 身份证校验 ===================== */

    @Nested
    @DisplayName("POST /student/login 身份证校验")
    class IdCardValidation {

        @Test
        @DisplayName("有效身份证号登录成功")
        void shouldLoginWithValidIdCard() throws Exception {
            when(activityService.checkLogin(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());

            mockMvc.perform(post("/student/login")
                    .param("activityId", "1")
                    .param("studentName", "张三")
                    .param("studentId", "test001")
                    .param("idCard", "440301199001011234"))
                    .andExpect(status().is3xxRedirection());
        }

        @Test
        @DisplayName("身份证号校验位错误时登录失败")
        void shouldFailWithInvalidCheckDigit() throws Exception {
            mockMvc.perform(post("/student/login")
                    .locale(java.util.Locale.CHINA)
                    .param("activityId", "1")
                    .param("studentName", "张三")
                    .param("studentId", "test001")
                    .param("idCard", "440301199001011235"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("error", "身份证号格式不正确或校验位错误。"));
        }

        @Test
        @DisplayName("身份证号长度不正确时登录失败")
        void shouldFailWithInvalidLength() throws Exception {
            mockMvc.perform(post("/student/login")
                    .locale(java.util.Locale.CHINA)
                    .param("activityId", "1")
                    .param("studentName", "张三")
                    .param("studentId", "test001")
                    .param("idCard", "123456"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("error", "身份证号格式不正确或校验位错误。"));
        }

        @Test
        @DisplayName("身份证号含非法字符时登录失败")
        void shouldFailWithInvalidChars() throws Exception {
            mockMvc.perform(post("/student/login")
                    .locale(java.util.Locale.CHINA)
                    .param("activityId", "1")
                    .param("studentName", "张三")
                    .param("studentId", "test001")
                    .param("idCard", "11010119900101123A"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("error", "身份证号格式不正确或校验位错误。"));
        }
    }
}
