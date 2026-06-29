package com.skillbridge.controller;

import com.skillbridge.service.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 单元测试。
 * 覆盖：业务异常展示友好消息、系统异常屏蔽内部细节。
 * 直接实例化处理器并调用方法，验证视图名、model 内容与 HTTP 状态码。
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MessageSource messageSource;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        when(messageSource.getMessage(any(String.class), any(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        handler = new GlobalExceptionHandler(messageSource);
    }

    @Nested
    @DisplayName("BusinessException 业务异常")
    class BusinessExceptionHandler {

        @Test
        @DisplayName("返回 common/error 视图且 model 包含异常消息，HTTP 400")
        void shouldReturnErrorViewWithBusinessMessage() {
            Model model = new ConcurrentModel();
            HttpServletResponse response = mock(HttpServletResponse.class);
            BusinessException ex = new BusinessException("活动不存在");

            String viewName = handler.handleBusiness(ex, model, response);

            assertEquals("common/error", viewName);
            assertEquals("活动不存在", model.getAttribute("error"));
            verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Exception 系统异常")
    class GenericExceptionHandler {

        @Test
        @DisplayName("返回 common/error 视图且 model 包含系统繁忙（不暴露异常细节），HTTP 500")
        void shouldReturnErrorViewWithSystemBusyMessage() {
            Model model = new ConcurrentModel();
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);
            when(request.getLocale()).thenReturn(Locale.CHINA);
            when(messageSource.getMessage(eq("error.system.busy"), any(), any(Locale.class)))
                    .thenReturn("系统繁忙，请稍后重试。如问题持续存在，请联系管理员。");
            Exception ex = new RuntimeException("NullPointerException: SQL=select * from users");

            String viewName = handler.handleAll(ex, request, model, response);

            assertEquals("common/error", viewName);
            String error = (String) model.getAttribute("error");
            assertNotNull(error);
            assertTrue(error.contains("系统繁忙"), "应包含系统繁忙提示");
            assertFalse(error.contains("SQL"), "不应暴露 SQL 细节");
            assertFalse(error.contains("NullPointerException"), "不应暴露异常堆栈");
            verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
