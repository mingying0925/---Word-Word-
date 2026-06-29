package com.skillbridge.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * CSRF 保护过滤器（Double Submit Cookie 模式）。
 * <p>
 * GET 请求：生成随机 CSRF Token 存入 Session，并设置到 Cookie（httpOnly=false，允许 JS 读取）。
 * POST/PUT/DELETE 请求：校验 Cookie 中的 Token 与请求头/参数中的 Token 是否一致，不一致返回 403。
 */
public class CsrfFilter extends OncePerRequestFilter {

    private static final String CSRF_TOKEN_ATTR = "csrfToken";
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String CSRF_PARAM_NAME = "_csrf";

    private static final String[] EXCLUDED_PATHS = {
            // 静态资源（避免每次加载 CSS/JS/图片 刷新 CSRF Token）
            "/css/", "/js/", "/images/", "/img/", "/webjars/", "/uploads/",
            "/favicon.ico",
            // 系统路径（非业务请求）
            "/actuator/", "/h2-console/", "/error", "/logout",
            // 登录接口（由限流过滤器保护）
            "/teacher/login", "/student/login"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String method = request.getMethod().toUpperCase();
        String path = request.getRequestURI();

        // 排除指定路径
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("GET".equals(method)) {
            // 仅在 session 中无 token 时生成新 token，避免多标签页互相覆盖
            String token = (String) request.getSession().getAttribute(CSRF_TOKEN_ATTR);
            if (token == null) {
                token = UUID.randomUUID().toString();
                request.getSession().setAttribute(CSRF_TOKEN_ATTR, token);
            }

            Cookie cookie = new Cookie(CSRF_COOKIE_NAME, token);
            cookie.setPath("/");
            cookie.setHttpOnly(false);
            cookie.setSecure(request.isSecure());
            response.addCookie(cookie);

            filterChain.doFilter(request, response);
        } else if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            // 校验 token
            String sessionToken = (String) request.getSession().getAttribute(CSRF_TOKEN_ATTR);
            if (sessionToken == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token missing");
                return;
            }

            String clientToken = getClientToken(request);
            if (clientToken == null || !sessionToken.equals(clientToken)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token mismatch");
                return;
            }

            filterChain.doFilter(request, response);
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private boolean isExcluded(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) {
                return true;
            }
        }
        return false;
    }

    private String getClientToken(HttpServletRequest request) {
        // 优先从请求头获取
        String headerToken = request.getHeader(CSRF_HEADER_NAME);
        if (headerToken != null && !headerToken.isBlank()) {
            return headerToken;
        }
        // 其次从请求参数获取
        return request.getParameter(CSRF_PARAM_NAME);
    }
}