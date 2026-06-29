package com.skillbridge.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API 版本信息控制器。
 * <p>
 * 提供 /api/v1/version 端点，返回当前应用的 API 版本、构建信息与运行状态。
 * 该端点无需鉴权（在 WebConfig 中排除），便于运维探活与版本核对。
 */
@RestController
@RequestMapping("/api/v1")
public class VersionController {

    @Value("${app.api-version:v1}")
    private String apiVersion;

    @Value("${spring.application.name:skillbridge}")
    private String appName;

    /** 应用版本号（来自 application.yml app.version） */
    @Value("${app.version:0.0.1}")
    private String appVersion;

    @GetMapping("/version")
    public Map<String, Object> version() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("name", appName);
        info.put("apiVersion", apiVersion);
        info.put("appVersion", appVersion);
        info.put("timestamp", LocalDateTime.now().toString());
        info.put("status", "UP");
        return info;
    }
}
