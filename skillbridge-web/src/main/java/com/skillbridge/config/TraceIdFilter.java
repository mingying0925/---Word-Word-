package com.skillbridge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪 ID 过滤器。
 * <p>
 * 为每个 HTTP 请求生成唯一的 traceId，写入 MDC（Mapped Diagnostic Context），
 * 使该请求链路的所有日志都携带 traceId，便于问题排查。
 * <p>
 * traceId 同时写入响应头 X-Trace-Id，便于前端/运维关联。
 * 同时在响应头中附加 X-API-Version，标识当前 API 版本。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String API_VERSION_HEADER = "X-API-Version";
    public static final String MDC_TRACE_ID = "traceId";

    @Value("${app.api-version:v1}")
    private String apiVersion;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先从请求头获取（支持链路传递），否则生成新 ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(API_VERSION_HEADER, apiVersion);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
