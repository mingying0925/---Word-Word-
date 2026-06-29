package com.skillbridge.config;

import com.skillbridge.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 拦截器 + 静态资源映射。
 * <p>
 * 拦截策略：
 * - 拦截：核心业务路径 /teacher/&#42;&#42;、/student/&#42;&#42;（含表单提交流程）。
 * - 放行：静态资源（css/js/images 等）、登录页路由、登录 API 接口、H2 控制台、错误页。
 * <p>
 * 说明：放行规则使用 Ant 风格通配符，按活动 ID 变化的学生登录接口
 * （/student/activity/&#42;/login）也能被精确排除，避免登录接口本身被拦截导致死循环。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Value("${app.upload-dir:${user.dir}/uploads}")
    private String uploadDir;

    /**
     * 允许跨域的来源列表（逗号分隔）。默认为空，表示不允许跨域。
     * 服务端渲染应用本身不需要跨域；如需对接独立前端，通过环境变量注入。
     */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    public WebConfig(JwtInterceptor jwtInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns(
                        // 拦截核心业务路径：教师端与学生端全部流程
                        "/teacher/**",
                        "/student/**"
                )
                .excludePathPatterns(
                        // —— 静态资源 ——
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/img/**",
                        "/webjars/**",
                        "/favicon.ico",

                        // —— 双入口登录页与登录接口（避免拦截器死循环）——
                        "/",                       // 首页重定向
                        "/logout",                 // 登出接口
                        "/teacher/login",          // 教师登录页 + 登录接口
                        "/student/login",          // 学生登录页 + 登录接口
                        // 旧活动入口仅重定向到学生登录页（单段路径 /student/activity/{id}）。
                        // 注意：使用单星号 /* 仅匹配一层路径段，避免误排除
                        // /form、/submit、/view-submission、/download 等需鉴权的子路径。
                        "/student/activity/*",

                        // —— API 接口（CSRF 保护除外路径）——
                        "/api/**",

                        // —— 其他无需鉴权的系统路径 ——
                        "/error",                  // Spring 错误页
                        "/h2-console/**",           // H2 数据库控制台（仅本地开发）
                        "/actuator/**"             // Actuator 监控端点
                );
    }

    /**
     * CORS 配置：服务端渲染应用本身不需要跨域。
     * 仅当 app.cors.allowed-origins 配置了具体来源时才放开，避免
     * allowedOriginPatterns("*") + allowCredentials(true) 的不安全组合。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            // 未配置允许来源时不注册 CORS 映射，浏览器默认同源策略生效
            return;
        }
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 静态资源映射。
     * <p>
     * 注意：不再将 uploads/ 目录映射为公开静态资源。
     * 上传的文件（含学生证件照等敏感 PII）必须通过鉴权后的 Controller 端点访问，
     * 防止 URL 枚举泄露隐私。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 仅保留标准静态资源目录，uploads/ 不再公开映射
    }

    /**
     * BCrypt 密码编码器 Bean。
     * <p>
     * 统一管理密码哈希强度，供 {@link com.skillbridge.service.TeacherService} 注入使用，
     * 便于测试 Mock 与未来调整强度参数。
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * RestTemplate Bean（供 {@link com.skillbridge.service.PythonExportClient} 调用 Python 微服务）。
     * <p>
     * 超时参数外部化，便于不同环境调优。默认连接 10s、读取 60s（导出大文档场景）。
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${app.export.rest-connect-timeout-ms:10000}") int connectTimeout,
            @Value("${app.export.rest-read-timeout-ms:60000}") int readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }
}
