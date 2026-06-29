package com.skillbridge.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * I18nConfig 单元测试。
 * 验证 MessageSource、LocaleResolver、LocaleChangeInterceptor 的配置。
 */
class I18nConfigTest {

    private final I18nConfig config = new I18nConfig();

    @Nested
    @DisplayName("MessageSource 配置")
    class MessageSourceTest {

        @Test
        @DisplayName("MessageSource 不为 null")
        void shouldCreateMessageSource() {
            MessageSource source = config.messageSource();
            assertNotNull(source);
        }

        @Test
        @DisplayName("中文环境下返回中文消息")
        void shouldReturnChineseMessage() {
            MessageSource source = config.messageSource();
            String msg = source.getMessage("nav.dashboard", null, Locale.SIMPLIFIED_CHINESE);
            assertEquals("仪表盘", msg);
        }

        @Test
        @DisplayName("英文环境下返回英文消息")
        void shouldReturnEnglishMessage() {
            MessageSource source = config.messageSource();
            String msg = source.getMessage("nav.dashboard", null, Locale.ENGLISH);
            assertEquals("Dashboard", msg);
        }

        @Test
        @DisplayName("带参数的消息正确替换占位符")
        void shouldResolveParameterizedMessage() {
            MessageSource source = config.messageSource();
            String msg = source.getMessage("teacher.roster.msg.imported",
                    new Object[]{10}, Locale.SIMPLIFIED_CHINESE);
            assertEquals("成功导入 10 条记录", msg);
        }

        @Test
        @DisplayName("未知消息 key 时返回 key 本身（useCodeAsDefaultMessage）")
        void shouldReturnKeyWhenMessageNotFound() {
            MessageSource source = config.messageSource();
            String msg = source.getMessage("nonexistent.key", null, Locale.SIMPLIFIED_CHINESE);
            assertEquals("nonexistent.key", msg);
        }
    }

    @Nested
    @DisplayName("LocaleResolver 配置")
    class LocaleResolverTest {

        @Test
        @DisplayName("LocaleResolver 不为 null")
        void shouldCreateLocaleResolver() {
            LocaleResolver resolver = config.localeResolver();
            assertNotNull(resolver);
        }
    }

    @Nested
    @DisplayName("LocaleChangeInterceptor 配置")
    class LocaleChangeInterceptorTest {

        @Test
        @DisplayName("拦截器参数名为 lang")
        void shouldCreateInterceptorWithLangParam() {
            LocaleChangeInterceptor interceptor = config.localeChangeInterceptor();
            assertNotNull(interceptor);
            // LocaleChangeInterceptor 的 paramName 通过 getParamName() 获取
            assertEquals("lang", interceptor.getParamName());
        }
    }
}
