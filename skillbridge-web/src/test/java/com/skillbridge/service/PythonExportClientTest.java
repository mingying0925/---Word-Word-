package com.skillbridge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PythonExportClient 单元测试。
 * 通过反射注入 Mock RestTemplate，验证对 Python 微服务的调用逻辑与异常处理。
 */
class PythonExportClientTest {

    @Mock
    private RestTemplate restTemplate;

    private PythonExportClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new PythonExportClient(restTemplate);
        // 注入配置（RestTemplate 已通过构造函数注入）
        ReflectionTestUtils.setField(client, "exportServiceUrl", "http://localhost:5000/api");
        // 测试中禁用重试，避免睡眠延迟
        ReflectionTestUtils.setField(client, "retryMax", 0);
    }

    /* ===================== parseBookmarks ===================== */

    @Test
    @DisplayName("成功解析书签返回 JSON 字符串")
    void shouldParseBookmarksSuccessfully() {
        MockMultipartFile file = new MockMultipartFile("file", "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx".getBytes());
        String expectedJson = "{\"bookmarks\":[],\"tables_structure\":[]}";
        ResponseEntity<String> resp = new ResponseEntity<>(expectedJson, HttpStatus.OK);
        when(restTemplate.postForEntity(eq("http://localhost:5000/api/parse-bookmarks"), any(), eq(String.class)))
                .thenReturn(resp);

        String result = client.parseBookmarks(file);
        assertEquals(expectedJson, result);
    }

    @Test
    @DisplayName("RestClientException 时抛出 BusinessException")
    void shouldThrowBusinessExceptionOnRestError() {
        MockMultipartFile file = new MockMultipartFile("file", "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx".getBytes());
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RestClientException("connection refused"));

        BusinessException ex = assertThrows(BusinessException.class, () -> client.parseBookmarks(file));
        assertTrue(ex.getMessage().contains("parseBookmarks") || ex.getMessage().contains("不可用"));
    }

    @Test
    @DisplayName("非 2xx 响应时抛出 BusinessException")
    void shouldThrowWhenNon2xxResponse() {
        MockMultipartFile file = new MockMultipartFile("file", "template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake-docx".getBytes());
        ResponseEntity<String> resp = new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        when(restTemplate.postForEntity(anyString(), any(), eq(String.class))).thenReturn(resp);

        BusinessException ex = assertThrows(BusinessException.class, () -> client.parseBookmarks(file));
        assertTrue(ex.getMessage().contains("Python 书签解析失败"));
    }

    /* ===================== fillWord ===================== */

    @Test
    @DisplayName("成功填充 Word 返回字节流")
    void shouldFillWordSuccessfully() {
        byte[] expected = "filled-docx".getBytes();
        ResponseEntity<byte[]> resp = new ResponseEntity<>(expected, HttpStatus.OK);
        when(restTemplate.postForEntity(eq("http://localhost:5000/api/fill-word"), any(), eq(byte[].class)))
                .thenReturn(resp);

        byte[] result = client.fillWord("/tmp/template.docx", Map.of("name", "张三"));
        assertArrayEquals(expected, result);
    }

    @Test
    @DisplayName("fillWord RestClientException 时抛出 BusinessException")
    void shouldThrowBusinessExceptionOnFillWordRestError() {
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class)))
                .thenThrow(new RestClientException("timeout"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.fillWord("/tmp/template.docx", Map.of("name", "张三")));
        assertTrue(ex.getMessage().contains("fillWord") || ex.getMessage().contains("不可用"));
    }

    @Test
    @DisplayName("fillWord 非 2xx 响应时抛出 BusinessException")
    void shouldThrowWhenFillWordNon2xx() {
        ResponseEntity<byte[]> resp = new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        when(restTemplate.postForEntity(anyString(), any(), eq(byte[].class))).thenReturn(resp);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> client.fillWord("/tmp/template.docx", Map.of("name", "张三")));
        assertTrue(ex.getMessage().contains("Python Word 填充失败"));
    }
}
