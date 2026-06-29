package com.skillbridge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString());
    }

    @Nested
    @DisplayName("store")
    class Store {

        @Test
        @DisplayName("保存文件成功返回绝对路径")
        void shouldStoreFile() {
            MultipartFile file = new MockMultipartFile("file", "test.docx", "application/octet-stream", "content".getBytes());

            String path = storageService.store(file);

            assertNotNull(path);
            assertTrue(path.startsWith(tempDir.toAbsolutePath().toString()));
            assertTrue(path.endsWith("test.docx"));
            assertTrue(Files.exists(Path.of(path)));
        }

        @Test
        @DisplayName("原始文件名为空时使用默认名")
        void shouldUseDefaultNameWhenOriginalNull() {
            MultipartFile file = new MockMultipartFile("file", (String) null, "application/octet-stream", "content".getBytes());

            String path = storageService.store(file);

            assertTrue(path.contains("_file"));
        }

        @Test
        @DisplayName("文件为空时抛出异常")
        void shouldThrowWhenFileNull() {
            assertThrows(BusinessException.class, () -> storageService.store(null));
        }

        @Test
        @DisplayName("文件内容为空时抛出异常")
        void shouldThrowWhenFileEmpty() {
            MultipartFile file = new MockMultipartFile("file", "empty.docx", "application/octet-stream", new byte[0]);
            assertThrows(BusinessException.class, () -> storageService.store(file));
        }
    }

    @Nested
    @DisplayName("load")
    class Load {

        @Test
        @DisplayName("读取已存在的文件")
        void shouldLoadExistingFile() throws Exception {
            Path file = tempDir.resolve("test.txt");
            Files.writeString(file, "hello");
            String savedPath = file.toAbsolutePath().toString();

            try (InputStream is = storageService.load(savedPath)) {
                byte[] content = is.readAllBytes();
                assertEquals("hello", new String(content, StandardCharsets.UTF_8));
            }
        }

        @Test
        @DisplayName("路径为空时抛出异常")
        void shouldThrowWhenPathNull() {
            assertThrows(BusinessException.class, () -> storageService.load(null));
        }

        @Test
        @DisplayName("文件不存在时抛出异常")
        void shouldThrowWhenFileNotFound() {
            assertThrows(BusinessException.class, () -> storageService.load(tempDir.resolve("nonexist.txt").toString()));
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("删除已存在的文件")
        void shouldDeleteExistingFile() throws Exception {
            Path file = tempDir.resolve("delete-me.txt");
            Files.writeString(file, "to delete");

            storageService.delete(file.toAbsolutePath().toString());

            assertFalse(Files.exists(file));
        }

        @Test
        @DisplayName("文件不存在时静默忽略")
        void shouldSilentlyIgnoreMissingFile() {
            assertDoesNotThrow(() -> storageService.delete(tempDir.resolve("missing.txt").toString()));
        }

        @Test
        @DisplayName("路径为空时静默忽略")
        void shouldSilentlyIgnoreNullPath() {
            assertDoesNotThrow(() -> storageService.delete(null));
        }
    }

    @Nested
    @DisplayName("resolveLocalPath")
    class ResolveLocalPath {

        @Test
        @DisplayName("返回正确的 Path 对象")
        void shouldReturnCorrectPath() {
            Path result = storageService.resolveLocalPath("C:/temp/file.txt");
            assertEquals(Path.of("C:/temp/file.txt"), result);
        }
    }
}
