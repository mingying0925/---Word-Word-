package com.skillbridge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Nested
    @DisplayName("非登录路径放行")
    class NonLoginPaths {

        @Test
        @DisplayName("GET 请求放行")
        void shouldPassGetRequest() throws Exception {
            when(request.getMethod()).thenReturn("GET");
            when(request.getRequestURI()).thenReturn("/teacher/activities");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("POST 非登录路径放行")
        void shouldPassPostOnNonLoginPath() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/create/confirm");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("登录接口限流")
    class LoginRateLimit {

        @Test
        @DisplayName("首次请求放行")
        void shouldPassFirstRequest() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/login");
            when(request.getRemoteAddr()).thenReturn("192.168.1.1");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("10 次内请求放行")
        void shouldAllowWithinLimit() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/login");
            when(request.getRemoteAddr()).thenReturn("192.168.1.2");

            for (int i = 0; i < 10; i++) {
                filter.doFilterInternal(request, response, filterChain);
            }

            verify(filterChain, times(10)).doFilter(request, response);
        }

        @Test
        @DisplayName("第 11 次请求返回 429")
        void shouldReturn429WhenExceedLimit() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/login");
            when(request.getRemoteAddr()).thenReturn("192.168.1.3");
            StringWriter sw = new StringWriter();
            when(response.getWriter()).thenReturn(new PrintWriter(sw));

            for (int i = 0; i < 10; i++) {
                filter.doFilterInternal(request, response, filterChain);
            }
            filter.doFilterInternal(request, response, filterChain);

            verify(response).setStatus(429);
            assertTrue(sw.toString().contains("请求过于频繁"));
            verify(filterChain, times(10)).doFilter(any(), any());
        }

        @Test
        @DisplayName("不同 IP 独立计数")
        void shouldTrackIpIndependently() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/login");
            when(request.getRemoteAddr()).thenReturn("10.0.0.1");

            for (int i = 0; i < 10; i++) {
                filter.doFilterInternal(request, response, filterChain);
            }

            when(request.getRemoteAddr()).thenReturn("10.0.0.2");
            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain, times(11)).doFilter(any(), any());
        }

        @Test
        @DisplayName("X-Forwarded-For 优先于 RemoteAddr")
        void shouldUseForwardedFor() throws Exception {
            when(request.getMethod()).thenReturn("POST");
            when(request.getRequestURI()).thenReturn("/teacher/login");
            lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
            when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verify(request).getHeader("X-Forwarded-For");
        }
    }
}
