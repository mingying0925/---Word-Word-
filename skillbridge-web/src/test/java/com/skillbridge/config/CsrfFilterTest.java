package com.skillbridge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsrfFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private CsrfFilter filter;

    @BeforeEach
    void setUp() {
        filter = new CsrfFilter();
    }

    @Nested
    @DisplayName("排除路径")
    class ExcludedPaths {

        @Test
        @DisplayName("GET /css/style.css 应放行")
        void shouldSkipCssPath() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/css/style.css");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(response, never()).addCookie(any());
        }

        @Test
        @DisplayName("POST /teacher/login 应放行")
        void shouldSkipTeacherLogin() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/login");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("POST /api/parse-bookmarks 应校验 CSRF Token")
        void shouldValidateApiPath() throws Exception {
            HttpSession session = new MockHttpSession();
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/api/parse-bookmarks");
            when(request.getSession()).thenReturn(session);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(403, "CSRF token missing");
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("GET 请求生成 CSRF Token")
    class GetRequest {

        @Captor
        private ArgumentCaptor<Cookie> cookieCaptor;

        @Test
        @DisplayName("GET 应生成 token 写入 session 和 cookie")
        void shouldGenerateTokenOnGet() throws Exception {
            HttpSession session = new MockHttpSession();
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/teacher/activities");
            when(request.getSession()).thenReturn(session);
            when(request.isSecure()).thenReturn(false);

            filter.doFilterInternal(request, response, filterChain);

            assertNotNull(session.getAttribute("csrfToken"));
            verify(response).addCookie(cookieCaptor.capture());
            Cookie cookie = cookieCaptor.getValue();
            assertEquals("XSRF-TOKEN", cookie.getName());
            assertEquals("/", cookie.getPath());
            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("POST 请求校验 CSRF Token")
    class PostRequest {

        @Test
        @DisplayName("Session 无 token 返回 403")
        void shouldReturn403WhenNoSessionToken() throws Exception {
            HttpSession session = new MockHttpSession();
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/create/confirm");
            when(request.getSession()).thenReturn(session);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(403, "CSRF token missing");
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("请求头 token 与 session 不一致返回 403")
        void shouldReturn403WhenTokenMismatch() throws Exception {
            HttpSession session = new MockHttpSession();
            session.setAttribute("csrfToken", "expected-token");
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/activity/1/close");
            when(request.getSession()).thenReturn(session);
            when(request.getHeader("X-XSRF-TOKEN")).thenReturn("wrong-token");

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(403, "CSRF token mismatch");
        }

        @Test
        @DisplayName("请求头 token 匹配时放行")
        void shouldPassWhenHeaderTokenMatches() throws Exception {
            HttpSession session = new MockHttpSession();
            session.setAttribute("csrfToken", "valid-token");
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/create/confirm");
            when(request.getSession()).thenReturn(session);
            when(request.getHeader("X-XSRF-TOKEN")).thenReturn("valid-token");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("请求参数 _csrf 匹配时放行")
        void shouldPassWhenParamTokenMatches() throws Exception {
            HttpSession session = new MockHttpSession();
            session.setAttribute("csrfToken", "param-token");
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/create/confirm");
            when(request.getSession()).thenReturn(session);
            when(request.getHeader("X-XSRF-TOKEN")).thenReturn(null);
            when(request.getParameter("_csrf")).thenReturn("param-token");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("PUT/DELETE 请求")
    class PutDeleteRequest {

        @Test
        @DisplayName("PUT 请求 token 校验生效")
        void shouldValidateTokenOnPut() throws Exception {
            HttpSession session = new MockHttpSession();
            when(request.getMethod()).thenReturn("PUT");
            when(request.getRequestURI()).thenReturn("/teacher/something");
            when(request.getSession()).thenReturn(session);

            filter.doFilterInternal(request, response, filterChain);

            verify(response).sendError(403, "CSRF token missing");
        }
    }
}
