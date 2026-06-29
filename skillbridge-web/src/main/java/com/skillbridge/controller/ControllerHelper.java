package com.skillbridge.controller;

import com.skillbridge.interceptor.JwtInterceptor;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 控制器层公共辅助工具。
 * <p>
 * 抽取 {@link TeacherController} 与 {@link TemplateController} 中重复实现的
 * "获取当前操作人"与"获取客户端 IP"逻辑，统一行为并引用 {@link JwtInterceptor} 常量，
 * 避免属性名字面量漂移。
 */
public final class ControllerHelper {

    private ControllerHelper() {
    }

    /**
     * 从 request 属性获取当前操作人工号（由 {@link JwtInterceptor} 注入）。
     * 若属性缺失（理论上不会发生，因拦截器已鉴权），返回 "unknown" 兜底。
     */
    public static String getCurrentOperator(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        return userId != null ? userId.toString() : "unknown";
    }

    /**
     * 获取客户端真实 IP，穿透反向代理。
     * <p>
     * 依次尝试 X-Forwarded-For、X-Real-IP，最后回退到 {@link HttpServletRequest#getRemoteAddr()}。
     * X-Forwarded-For 含多级代理时取第一个（最原始客户端）。忽略值为 "unknown" 的头。
     */
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
