package com.skillbridge.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Cookie 工具类。
 * <p>
 * 统一管理 JWT Cookie 的写入与清除，确保安全属性（HttpOnly / Secure / SameSite）一致。
 * <p>
 * 安全属性说明：
 * <ul>
 *   <li>HttpOnly：禁止 JavaScript 访问，防 XSS 窃取。</li>
 *   <li>Secure：仅 HTTPS 传输，生产环境必须开启。</li>
 *   <li>SameSite=Lax：防 CSRF，允许顶级导航携带 Cookie。</li>
 * </ul>
 * 由于 Servlet API 的 {@link jakarta.servlet.http.Cookie} 不支持 SameSite 属性，
 * 本工具通过手动拼接 Set-Cookie 响应头实现。
 */
@Component
public class CookieHelper {

    private final String cookieName;
    private final boolean secure;
    private final String sameSite;
    private final long expirationSeconds;

    public CookieHelper(@Value("${jwt.cookie-name:skillbridge_token}") String cookieName,
                        @Value("${jwt.cookie-secure:false}") boolean secure,
                        @Value("${jwt.cookie-same-site:Lax}") String sameSite,
                        @Value("${jwt.expiration:7200000}") long expirationMillis) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.expirationSeconds = expirationMillis / 1000;
    }

    /**
     * 写入 JWT Cookie（登录成功后调用）。
     *
     * @param response HTTP 响应
     * @param token    JWT 字符串
     */
    public void writeTokenCookie(HttpServletResponse response, String token) {
        String cookieValue = String.format(
                "%s=%s; Path=/; Max-Age=%d; HttpOnly%s; SameSite=%s",
                cookieName,
                URLEncoder.encode(token, StandardCharsets.UTF_8),
                expirationSeconds,
                secure ? "; Secure" : "",
                sameSite
        );
        response.addHeader("Set-Cookie", cookieValue);
    }

    /**
     * 清除 JWT Cookie（登出时调用）。
     * 通过设置 Max-Age=0 立即失效。
     *
     * @param response HTTP 响应
     */
    public void clearTokenCookie(HttpServletResponse response) {
        String cookieValue = String.format(
                "%s=; Path=/; Max-Age=0; HttpOnly%s; SameSite=%s",
                cookieName,
                secure ? "; Secure" : "",
                sameSite
        );
        response.addHeader("Set-Cookie", cookieValue);
    }

    /** 获取 Cookie 名称 */
    public String getCookieName() {
        return cookieName;
    }
}
