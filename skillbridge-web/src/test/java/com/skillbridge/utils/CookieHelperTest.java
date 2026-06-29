package com.skillbridge.utils;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CookieHelperTest {

    @Mock
    private HttpServletResponse response;

    @Captor
    private ArgumentCaptor<String> headerCaptor;

    @Nested
    @DisplayName("writeTokenCookie 写入 Cookie")
    class WriteTokenCookie {

        @Test
        @DisplayName("写入正确的 Set-Cookie 头（非 Secure）")
        void shouldWriteCookieWithoutSecure() {
            CookieHelper helper = new CookieHelper("my_token", false, "Lax", 7200000);

            helper.writeTokenCookie(response, "jwt-token-value");

            verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            String header = headerCaptor.getValue();
            assertTrue(header.startsWith("my_token=jwt-token-value;"));
            assertTrue(header.contains("Path=/;"));
            assertTrue(header.contains("Max-Age=7200;"));
            assertTrue(header.contains("HttpOnly"));
            assertTrue(header.contains("SameSite=Lax"));
            assertFalse(header.contains("Secure"));
        }

        @Test
        @DisplayName("Secure 模式写入 ; Secure 标记")
        void shouldWriteCookieWithSecure() {
            CookieHelper helper = new CookieHelper("token", true, "Strict", 7200000);

            helper.writeTokenCookie(response, "secure-token");

            verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            assertTrue(headerCaptor.getValue().contains("; Secure"));
        }

        @Test
        @DisplayName("URL 编码 token 值")
        void shouldUrlEncodeToken() {
            CookieHelper helper = new CookieHelper("token", false, "Lax", 7200000);

            helper.writeTokenCookie(response, "a+b/c");

            verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            assertTrue(headerCaptor.getValue().contains("a%2Bb%2Fc"));
        }
    }

    @Nested
    @DisplayName("clearTokenCookie 清除 Cookie")
    class ClearTokenCookie {

        @Test
        @DisplayName("写入 Max-Age=0 的清除 Cookie")
        void shouldClearCookie() {
            CookieHelper helper = new CookieHelper("skillbridge_token", false, "Lax", 7200000);

            helper.clearTokenCookie(response);

            verify(response).addHeader(eq("Set-Cookie"), headerCaptor.capture());
            String header = headerCaptor.getValue();
            assertTrue(header.startsWith("skillbridge_token=;"));
            assertTrue(header.contains("Max-Age=0;"));
            assertTrue(header.contains("HttpOnly"));
        }
    }

    @Nested
    @DisplayName("getCookieName")
    class GetCookieName {

        @Test
        @DisplayName("返回构造时传入的 cookieName")
        void shouldReturnCookieName() {
            CookieHelper helper = new CookieHelper("custom_name", false, "Lax", 7200000);

            assertEquals("custom_name", helper.getCookieName());
        }
    }
}
