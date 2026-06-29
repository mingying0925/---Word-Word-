package com.skillbridge.interceptor;

import com.skillbridge.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtInterceptor 单元测试。
 * 覆盖：无 Cookie 重定向、无效 Token、越权防护、正常放行。
 * 使用 Mockito 直接 mock HttpServletRequest/HttpServletResponse 与 JwtUtil。
 */
@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    private static final String COOKIE_NAME = "skillbridge_token";

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Claims claims;

    private JwtInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new JwtInterceptor(jwtUtil, COOKIE_NAME);
    }

    /* ===================== 无 Cookie（未登录） ===================== */

    @Nested
    @DisplayName("无 Cookie 未登录场景")
    class NoCookie {

        @Test
        @DisplayName("访问 /teacher/** 重定向到 /teacher/login?error=timeout 并返回 false")
        void shouldRedirectToTeacherLoginWhenNoCookie() throws Exception {
            when(request.getRequestURI()).thenReturn("/teacher/activities");
            when(request.getCookies()).thenReturn(null);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertFalse(result);
            verify(response).sendRedirect("/teacher/login?error=timeout");
        }

        @Test
        @DisplayName("访问 /student/** 重定向到 /student/login?error=timeout 并返回 false")
        void shouldRedirectToStudentLoginWhenNoCookie() throws Exception {
            when(request.getRequestURI()).thenReturn("/student/activity/1/form");
            when(request.getCookies()).thenReturn(null);

            boolean result = interceptor.preHandle(request, response, new Object());

            assertFalse(result);
            verify(response).sendRedirect("/student/login?error=timeout");
        }
    }

    /* ===================== 无效/被篡改 Token ===================== */

    @Nested
    @DisplayName("无效/被篡改 Token 场景")
    class InvalidToken {

        @Test
        @DisplayName("解析失败时重定向并附带 ?error=timeout")
        void shouldRedirectWhenTokenInvalid() throws Exception {
            String invalidToken = "tampered.token.value";
            when(request.getRequestURI()).thenReturn("/teacher/activities");
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie(COOKIE_NAME, invalidToken)
            });
            when(jwtUtil.parseToken(invalidToken)).thenThrow(new RuntimeException("invalid token"));

            boolean result = interceptor.preHandle(request, response, new Object());

            assertFalse(result);
            verify(response).sendRedirect("/teacher/login?error=timeout");
        }
    }

    /* ===================== 教师 Token 鉴权 ===================== */

    @Nested
    @DisplayName("教师 Token 鉴权")
    class TeacherToken {

        @Test
        @DisplayName("访问 /teacher/activities 放行并设置 request 属性")
        void shouldAllowTeacherAccessTeacherPath() throws Exception {
            String token = "valid-teacher-token";
            when(request.getRequestURI()).thenReturn("/teacher/activities");
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie(COOKIE_NAME, token)
            });
            when(jwtUtil.parseToken(token)).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn("T001");
            when(jwtUtil.getRole(claims)).thenReturn("teacher");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertTrue(result);
            verify(request).setAttribute(JwtInterceptor.ATTR_USER_ID, "T001");
            verify(request).setAttribute(JwtInterceptor.ATTR_ROLE, "teacher");
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("访问 /student/** 越权重定向 ?error=forbidden")
        void shouldForbidTeacherAccessStudentPath() throws Exception {
            String token = "valid-teacher-token";
            when(request.getRequestURI()).thenReturn("/student/activity/1/form");
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie(COOKIE_NAME, token)
            });
            when(jwtUtil.parseToken(token)).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn("T001");
            when(jwtUtil.getRole(claims)).thenReturn("teacher");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertFalse(result);
            verify(response).sendRedirect("/student/login?error=forbidden");
        }
    }

    /* ===================== 学生 Token 鉴权 ===================== */

    @Nested
    @DisplayName("学生 Token 鉴权")
    class StudentToken {

        @Test
        @DisplayName("访问 /student/** 放行并设置 request 属性")
        void shouldAllowStudentAccessStudentPath() throws Exception {
            String token = "valid-student-token";
            when(request.getRequestURI()).thenReturn("/student/activity/1/form");
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie(COOKIE_NAME, token)
            });
            when(jwtUtil.parseToken(token)).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn("2024001");
            when(jwtUtil.getRole(claims)).thenReturn("student");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertTrue(result);
            verify(request).setAttribute(JwtInterceptor.ATTR_USER_ID, "2024001");
            verify(request).setAttribute(JwtInterceptor.ATTR_ROLE, "student");
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("访问 /teacher/** 越权重定向 ?error=forbidden")
        void shouldForbidStudentAccessTeacherPath() throws Exception {
            String token = "valid-student-token";
            when(request.getRequestURI()).thenReturn("/teacher/activities");
            when(request.getCookies()).thenReturn(new Cookie[]{
                    new Cookie(COOKIE_NAME, token)
            });
            when(jwtUtil.parseToken(token)).thenReturn(claims);
            when(jwtUtil.getUserId(claims)).thenReturn("2024001");
            when(jwtUtil.getRole(claims)).thenReturn("student");

            boolean result = interceptor.preHandle(request, response, new Object());

            assertFalse(result);
            verify(response).sendRedirect("/teacher/login?error=forbidden");
        }
    }
}
