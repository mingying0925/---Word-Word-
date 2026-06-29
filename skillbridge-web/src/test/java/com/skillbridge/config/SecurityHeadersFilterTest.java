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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SecurityHeadersFilter 单元测试。
 * <p>
 * 验证安全响应头注入逻辑：基础头始终注入，CSP 受开关控制，额外脚本来源正确拼接。
 */
@ExtendWith(MockitoExtension.class)
class SecurityHeadersFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @Captor
    private ArgumentCaptor<String> headerValueCaptor;

    private SecurityHeadersFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SecurityHeadersFilter();
    }

    @Test
    @DisplayName("基础安全头始终注入（无论 CSP 是否开启）")
    void shouldAlwaysSetBaseSecurityHeaders() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", true);

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq("X-Content-Type-Options"), eq("nosniff"));
        verify(response).setHeader(eq("X-Frame-Options"), eq("DENY"));
        verify(response).setHeader(eq("Referrer-Policy"), eq("strict-origin-when-cross-origin"));
        verify(response).setHeader(eq("X-XSS-Protection"), eq("1; mode=block"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("CSP 开启时注入 Content-Security-Policy 头")
    void shouldSetCspHeaderWhenEnabled() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", true);
        ReflectionTestUtils.setField(filter, "extraScriptSrc", "");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq("Content-Security-Policy"), headerValueCaptor.capture());
        String csp = headerValueCaptor.getValue();
        // 核心指令齐备
        assertTrue(csp.contains("default-src 'self'"), "CSP 应包含 default-src 'self'");
        assertTrue(csp.contains("script-src 'self' 'unsafe-inline'"), "CSP 应包含 script-src 'self' 'unsafe-inline'");
        assertTrue(csp.contains("style-src 'self' 'unsafe-inline'"), "CSP 应包含 style-src 'self' 'unsafe-inline'");
        assertTrue(csp.contains("img-src 'self' data: blob:"), "CSP 应包含 img-src 'self' data: blob:");
        assertTrue(csp.contains("frame-ancestors 'none'"), "CSP 应包含 frame-ancestors 'none'");
        assertTrue(csp.contains("form-action 'self'"), "CSP 应包含 form-action 'self'");
        assertTrue(csp.contains("object-src 'none'"), "CSP 应包含 object-src 'none'");
    }

    @Test
    @DisplayName("CSP 关闭时不注入 Content-Security-Policy 头")
    void shouldNotSetCspHeaderWhenDisabled() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", false);

        filter.doFilter(request, response, filterChain);

        verify(response, never()).setHeader(eq("Content-Security-Policy"), anyString());
        // 基础安全头仍应注入
        verify(response).setHeader(eq("X-Content-Type-Options"), eq("nosniff"));
    }

    @Test
    @DisplayName("配置额外脚本来源时拼接到 script-src")
    void shouldAppendExtraScriptSources() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", true);
        ReflectionTestUtils.setField(filter, "extraScriptSrc", "https://cdn.example.com, https://analytics.example.com");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq("Content-Security-Policy"), headerValueCaptor.capture());
        String csp = headerValueCaptor.getValue();
        assertTrue(csp.contains("https://cdn.example.com"), "CSP script-src 应包含额外来源 cdn.example.com");
        assertTrue(csp.contains("https://analytics.example.com"), "CSP script-src 应包含额外来源 analytics.example.com");
    }

    @Test
    @DisplayName("额外脚本来源含空白项时自动忽略空项")
    void shouldIgnoreBlankExtraScriptSources() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", true);
        ReflectionTestUtils.setField(filter, "extraScriptSrc", "  https://cdn.example.com  , , ");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq("Content-Security-Policy"), headerValueCaptor.capture());
        String csp = headerValueCaptor.getValue();
        assertTrue(csp.contains("https://cdn.example.com"), "应包含非空来源");
        // 不应出现连续两个空格（来自空项拼接）
        assertFalse(csp.contains("  "), "不应保留空白项导致的连续空格");
    }

    @Test
    @DisplayName("extraScriptSrc 为空时 script-src 仅含默认值")
    void shouldUseDefaultScriptSrcWhenNoExtras() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", true);
        ReflectionTestUtils.setField(filter, "extraScriptSrc", "");

        filter.doFilter(request, response, filterChain);

        verify(response).setHeader(eq("Content-Security-Policy"), headerValueCaptor.capture());
        String csp = headerValueCaptor.getValue();
        // 提取 script-src 指令片段
        String scriptSrcPart = csp.substring(csp.indexOf("script-src"), csp.indexOf(";", csp.indexOf("script-src")));
        assertEquals("script-src 'self' 'unsafe-inline'", scriptSrcPart.trim());
    }

    @Test
    @DisplayName("filterChain 异常时安全头已注入（在 chain.doFilter 之前设置）")
    void shouldSetHeadersEvenWhenChainThrows() throws Exception {
        ReflectionTestUtils.setField(filter, "cspEnabled", false);
        doThrow(new RuntimeException("downstream error")).when(filterChain).doFilter(request, response);

        assertThrows(RuntimeException.class, () ->
                filter.doFilter(request, response, filterChain));

        // 安全头在 chain.doFilter 之前已注入
        verify(response).setHeader(eq("X-Content-Type-Options"), eq("nosniff"));
    }
}
