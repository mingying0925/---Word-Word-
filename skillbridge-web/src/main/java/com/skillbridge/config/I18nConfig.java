package com.skillbridge.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.Locale;

/**
 * 国际化（i18n）配置。
 * <p>
 * 支持中英文切换：
 * <ul>
 *   <li>CookieLocaleResolver：将语言偏好持久化到 Cookie（有效期 30 天）</li>
 *   <li>LocaleChangeInterceptor：通过 ?lang=en 参数切换语言</li>
 *   <li>MessageSource：从 classpath:i18n/messages 读取消息资源</li>
 * </ul>
 * 默认语言为中文（zh_CN）。
 */
@Configuration
public class I18nConfig implements WebMvcConfigurer {

    /** 消息资源 basename */
    private static final String BASENAME = "i18n/messages";

    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames(BASENAME);
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver();
        resolver.setCookieName("lang");
        resolver.setCookieMaxAge(30 * 24 * 60 * 60); // 30 天
        resolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }
}
