package com.skillbridge.controller;

import com.skillbridge.interceptor.JwtInterceptor;
import com.skillbridge.utils.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VersionController 单元测试。
 * 验证 /api/v1/version 端点返回正确的版本信息 JSON。
 */
@WebMvcTest(VersionController.class)
class VersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtInterceptor jwtInterceptor;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("GET /api/v1/version 返回版本信息 JSON")
    void shouldReturnVersionInfo() throws Exception {
        mockMvc.perform(get("/api/v1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("skillbridge"))
                .andExpect(jsonPath("$.apiVersion").value("v1"))
                .andExpect(jsonPath("$.appVersion").exists())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("GET /api/v1/version 响应包含 X-API-Version 头")
    void shouldReturnApiVersionHeader() throws Exception {
        mockMvc.perform(get("/api/v1/version"))
                .andExpect(header().exists("X-API-Version"))
                .andExpect(header().string("X-API-Version", "v1"));
    }
}
