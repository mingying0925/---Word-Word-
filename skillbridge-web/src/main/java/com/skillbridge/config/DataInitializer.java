package com.skillbridge.config;

import com.skillbridge.service.TeacherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据初始化配置。
 * <p>
 * 应用启动时自动初始化默认教师账号（仅首次启动、数据库为空时执行）。
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initData(TeacherService teacherService) {
        return args -> {
            try {
                teacherService.initDefaultTeacherIfNeeded();
            } catch (Exception e) {
                log.error("默认教师账号初始化失败", e);
            }
        };
    }
}
