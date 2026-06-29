package com.skillbridge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TraceIdFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private TraceIdFilter filter;

    @Captor
    private ArgumentCaptor<String> traceIdCaptor;

    @BeforeEach
    void setUp() {
        filter = new TraceIdFilter();
    }

    @Test
    @DisplayName("无传入 traceId 时生成新的 16 位 traceId 并写入响应头")
    void shouldGenerateTraceIdWhenNotPresent() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Trace-Id"), traceIdCaptor.capture());
        String traceId = traceIdCaptor.getValue();
        assertNotNull(traceId);
        assertEquals(16, traceId.length());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("传入 traceId 透传使用并写入响应头")
    void shouldUseIncomingTraceId() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("incoming-trace-123");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader("X-Trace-Id", "incoming-trace-123");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("请求完成后清理 MDC")
    void shouldCleanMdcAfterRequest() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("test-trace-id");

        filter.doFilterInternal(request, response, filterChain);

        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("filterChain 异常时仍清理 MDC")
    void shouldCleanMdcEvenOnException() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("test-trace-id");
        doThrow(new RuntimeException("filter error")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () ->
                filter.doFilterInternal(request, response, filterChain));

        assertNull(MDC.get("traceId"));
    }

    @Test
    @DisplayName("空白 traceId 头应生成新 ID")
    void shouldGenerateWhenHeaderIsBlank() throws Exception {
        when(request.getHeader("X-Trace-Id")).thenReturn("   ");

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setHeader(eq("X-Trace-Id"), traceIdCaptor.capture());
        assertEquals(16, traceIdCaptor.getValue().length());
    }
}
