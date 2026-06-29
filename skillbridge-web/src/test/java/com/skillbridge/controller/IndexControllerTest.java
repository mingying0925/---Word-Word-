package com.skillbridge.controller;

import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.utils.CookieHelper;
import com.skillbridge.utils.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * IndexController 集成测试。
 * 覆盖：门户页展示、登出重定向、Cookie 清理。
 * 路由 / 与 /logout 已在 WebConfig 中排除拦截器，无需 stub JwtInterceptor。
 */
@WebMvcTest(IndexController.class)
class IndexControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CookieHelper cookieHelper;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("GET / 返回门户页 common/portal，状态 200")
    void shouldReturnPortalPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("common/portal"));
    }

    @Nested
    @DisplayName("GET /logout 登出")
    class Logout {

        @Test
        @DisplayName("重定向到 /?logout=1，状态 3xx")
        void shouldRedirectToHomeWithLogoutParam() throws Exception {
            mockMvc.perform(get("/logout"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/?logout=1"));
        }

        @Test
        @DisplayName("调用 cookieHelper.clearTokenCookie 清除 Cookie")
        void shouldClearTokenCookie() throws Exception {
            mockMvc.perform(get("/logout"));

            verify(cookieHelper).clearTokenCookie(any());
        }
    }
}
