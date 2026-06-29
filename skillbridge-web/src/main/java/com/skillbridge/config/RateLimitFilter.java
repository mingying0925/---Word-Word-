package com.skillbridge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 登录接口限流过滤器（滑动窗口）。
 * <p>
 * 仅对 POST /teacher/login 和 /student/login 进行限流，
 * 每个 IP 每分钟最多 10 次请求，超限返回 429。
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_MS = 60_000;
    private static final long EVICTION_INTERVAL_MS = 300_000;

    private final ConcurrentHashMap<String, LinkedList<Long>> requestTimestamps = new ConcurrentHashMap<>();

    public RateLimitFilter() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-evictor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::evictStaleEntries, EVICTION_INTERVAL_MS, EVICTION_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void evictStaleEntries() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        for (Map.Entry<String, LinkedList<Long>> entry : requestTimestamps.entrySet()) {
            LinkedList<Long> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty() && timestamps.getFirst() < cutoff) {
                    timestamps.removeFirst();
                }
                if (timestamps.isEmpty()) {
                    requestTimestamps.remove(entry.getKey(), timestamps);
                }
            }
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        // 仅限流 POST 登录接口
        if (!"POST".equals(method)
                || (!"/teacher/login".equals(path) && !"/student/login".equals(path))) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = getClientIp(request);
        long now = System.currentTimeMillis();

        LinkedList<Long> timestamps = requestTimestamps.computeIfAbsent(ip, k -> new LinkedList<>());

        synchronized (timestamps) {
            // 清理窗口外的旧记录
            while (!timestamps.isEmpty() && now - timestamps.getFirst() > WINDOW_MS) {
                timestamps.removeFirst();
            }

            if (timestamps.size() >= MAX_REQUESTS) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"error\":\"请求过于频繁，请稍后再试\"}");
                response.getWriter().flush();
                return;
            }

            timestamps.addLast(now);
        }

        filterChain.doFilter(request, response);

        // 清理空列表，防止内存泄漏
        if (timestamps.isEmpty()) {
            requestTimestamps.remove(ip, timestamps);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String firstIp = forwarded.split(",")[0].trim();
            if (isValidIp(firstIp)) {
                return firstIp;
            }
        }
        return request.getRemoteAddr();
    }

    private static boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")
                || ip.matches("^[0-9a-fA-F:]+$");
    }
}