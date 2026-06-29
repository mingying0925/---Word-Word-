package com.skillbridge.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 安全响应头过滤器。
 * <p>
 * 为所有响应注入安全相关的 HTTP 头，提升整体安全基线：
 * <ul>
 *   <li>Content-Security-Policy：限制脚本/样式/图片来源，防 XSS 注入。</li>
 *   <li>X-Content-Type-Options: nosniff：防 MIME 嗅探。</li>
 *   <li>X-Frame-Options: DENY：防点击劫持（与 CSP frame-ancestors 互补）。</li>
 *   <li>Referrer-Policy：限制 Referer 泄露。</li>
 *   <li>X-XSS-Protection：旧版浏览器 XSS 过滤（已被 CSP 取代，保留兜底）。</li>
 * </ul>
 * <p>
 * CSP 策略说明：
 * <ul>
 *   <li>default-src 'self'：默认仅允许同源资源。</li>
 *   <li>script-src 'self' 'unsafe-inline'：允许内联脚本（Thymeleaf 页面内联 JS 较多，暂放宽）。</li>
 *   <li>style-src 'self' 'unsafe-inline'：允许内联样式（玻璃拟态大量内联 style）。</li>
 *   <li>img-src 'self' data: blob:：允许 data URI 与 blob 图片（学生证件照预览）。</li>
 *   <li>font-src 'self'：字体仅同源。</li>
 *   <li>frame-ancestors 'none'：禁止被任何页面嵌入 iframe。</li>
 *   <li>form-action 'self'：表单仅提交到同源。</li>
 *   <li>base-uri 'self'：base 标签仅同源。</li>
 *   <li>object-src 'none'：禁止 Flash/Java 插件。</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class SecurityHeadersFilter implements Filter {

    /** 是否启用 CSP（开发环境可关闭以便调试） */
    @Value("${app.security.csp-enabled:true}")
    private boolean cspEnabled;

    /** 允许的额外脚本来源（如对接外部 CDN），逗号分隔 */
    @Value("${app.security.csp-extra-script-src:}")
    private String extraScriptSrc;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 安全头（始终注入，开销极小）
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");

        if (cspEnabled) {
            StringBuilder scriptSrc = new StringBuilder("'self' 'unsafe-inline'");
            if (extraScriptSrc != null && !extraScriptSrc.isBlank()) {
                String[] extras = extraScriptSrc.split(",");
                for (String e : extras) {
                    String trimmed = e.trim();
                    if (!trimmed.isEmpty()) {
                        scriptSrc.append(' ').append(trimmed);
                    }
                }
            }
            String csp = String.format(
                    "default-src 'self'; " +
                    "script-src %s; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data: blob:; " +
                    "font-src 'self'; " +
                    "connect-src 'self'; " +
                    "frame-ancestors 'none'; " +
                    "form-action 'self'; " +
                    "base-uri 'self'; " +
                    "object-src 'none'",
                    scriptSrc.toString()
            );
            httpResponse.setHeader("Content-Security-Policy", csp);
        }

        chain.doFilter(request, response);
    }
}
