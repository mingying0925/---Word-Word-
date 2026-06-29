package com.skillbridge.interceptor;

import com.skillbridge.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器（双端隔离版）。
 * <p>
 * 工作流程（无状态，不依赖服务端 Session/Redis）：
 * 1. 从请求的 Cookie 中提取名为 {@code skillbridge_token} 的 JWT。
 * 2. 解析并校验 Token：为空 / 被篡改 / 已过期 → 拦截请求，按请求 URL 前缀
 *    分流重定向到对应端的登录页（/teacher/** → /teacher/login，/student/** → /student/login），
 *    并附带 {@code ?error=timeout} 提示参数。
 * 3. <b>越权防护</b>：校验 Token 中的 role 与请求 URL 前缀是否匹配。
 *    教师操作（/teacher/**）要求 role=teacher，学生操作（/student/**）要求 role=student。
 *    角色不匹配时重定向到对应端登录页并附带 {@code ?error=forbidden}，杜绝跨端越权访问。
 * 4. 校验成功 → 将解析出的用户信息（userId、role）通过 {@link HttpServletRequest#setAttribute}
 *    透传给后续 Controller。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    /** 存入 request attribute 的用户标识 key，Controller 可通过此 key 读取 */
    public static final String ATTR_USER_ID = "currentUserId";
    /** 存入 request attribute 的角色 key */
    public static final String ATTR_ROLE = "currentRole";

    private final JwtUtil jwtUtil;
    private final String cookieName;

    /**
     * @param jwtUtil    JWT 工具类
     * @param cookieName jwt.cookie-name，存放 Token 的 Cookie 名称
     */
    public JwtInterceptor(JwtUtil jwtUtil,
                          @Value("${jwt.cookie-name:skillbridge_token}") String cookieName) {
        this.jwtUtil = jwtUtil;
        this.cookieName = cookieName;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 从 Cookie 中提取 JWT
        String token = extractToken(request);

        // 2. Token 为空 → 未登录，按前缀分流重定向到对应端登录页
        if (token == null || token.isBlank()) {
            return redirectToLogin(request, response, "timeout");
        }

        // 3. 解析 Token，校验签名与有效期
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            // 被篡改(SignatureException) / 已过期(ExpiredJwtException) / 格式非法(JwtException)
            return redirectToLogin(request, response, "timeout");
        }

        // 4. 越权防护：校验 role 与 URL 前缀是否匹配
        String userId = jwtUtil.getUserId(claims);
        String role = jwtUtil.getRole(claims);
        String uri = request.getRequestURI();
        if (!isRoleMatchPrefix(role, uri)) {
            return redirectToLogin(request, response, "forbidden");
        }

        // 5. 校验成功：将用户信息存入 request 属性，供 Controller 直接取用
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_ROLE, role);
        // 同步写入 MDC，使每条日志（Controller → Service → Python 调用）均可按用户关联
        MDC.put("userId", userId);

        return true; // 放行
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        // 请求结束清理 MDC，避免线程池复用导致用户标识串号
        MDC.remove("userId");
    }

    /**
     * 校验 JWT 角色与请求 URL 前缀是否匹配。
     * <ul>
     *   <li>/teacher/** 要求 role=teacher</li>
     *   <li>/student/** 要求 role=student</li>
     * </ul>
     *
     * @param role Token 中的角色
     * @param uri  请求 URI
     * @return true 表示角色与路径匹配，允许访问
     */
    private boolean isRoleMatchPrefix(String role, String uri) {
        if (uri.startsWith("/teacher/")) {
            return "teacher".equals(role);
        }
        if (uri.startsWith("/student/")) {
            return "student".equals(role);
        }
        // 非双端前缀的路径不做角色限制（如静态资源，已被 WebConfig 排除）
        return true;
    }

    /**
     * 从请求的 Cookie 数组中查找指定名称的 Token。
     *
     * @return 找到则返回 Token 值，否则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * 按请求 URL 前缀分流到对应端登录页，并附带提示参数。
     * <ul>
     *   <li>/teacher/** → /teacher/login</li>
     *   <li>其余（含 /student/**）→ /student/login</li>
     * </ul>
     *
     * @param errorType 错误类型：timeout（未登录/过期）或 forbidden（角色越权）
     * 返回 false 以中断后续 Handler 执行。
     */
    private boolean redirectToLogin(HttpServletRequest request,
                                    HttpServletResponse response,
                                    String errorType) throws Exception {
        String uri = request.getRequestURI();
        String target = uri.startsWith("/teacher/") ? "/teacher/login" : "/student/login";
        response.sendRedirect(target + "?error=" + errorType);
        return false;
    }
}
