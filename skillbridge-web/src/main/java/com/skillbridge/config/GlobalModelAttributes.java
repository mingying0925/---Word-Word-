package com.skillbridge.config;

import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Date;

/**
 * 全局 Model 属性注入。
 * <p>
 * 为所有 Controller 渲染的视图注入通用变量：
 * <ul>
 *   <li>sessionExpiresAt：JWT 过期时间戳（毫秒），供前端 session-timeout.js 预警使用。</li>
 * </ul>
 * 仅在已登录页面（Cookie 中存在有效 JWT）时注入。
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final JwtUtil jwtUtil;
    private final String cookieName;

    public GlobalModelAttributes(JwtUtil jwtUtil,
                                 @Value("${jwt.cookie-name:skillbridge_token}") String cookieName) {
        this.jwtUtil = jwtUtil;
        this.cookieName = cookieName;
    }

    @ModelAttribute
    public void injectSessionExpiry(HttpServletRequest request, Model model) {
        // 仅在已通过拦截器鉴权的请求中注入（ATTR_USER_ID 存在表示已登录）
        if (request.getAttribute(JwtInterceptor.ATTR_USER_ID) == null) {
            return;
        }
        String token = extractToken(request);
        if (token == null) return;
        try {
            Claims claims = jwtUtil.parseToken(token);
            Date exp = claims.getExpiration();
            if (exp != null) {
                model.addAttribute("sessionExpiresAt", exp.getTime());
            }
        } catch (Exception ignored) {
            // 解析失败不注入，session-timeout.js 不会启用
        }
    }

    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (cookieName.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
