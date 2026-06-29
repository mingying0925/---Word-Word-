package com.skillbridge.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地文件系统存储实现。
 * <p>
 * 文件保存到 {@code app.upload-dir} 配置的目录（默认 ${user.dir}/uploads）。
 * 文件名格式：yyyyMMddHHmmss_{uuid8}_{原始文件名}，防止重名。
 */
@Service
@Primary
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);
    private final String uploadDir;

    public LocalFileStorageService(@Value("${app.upload-dir:${user.dir}/uploads}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件为空，无法保存");
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String original = file.getOriginalFilename();
            // 防御路径穿越：剥离任何路径分隔符，仅保留文件名部分。
            // 防止恶意客户端通过 originalFilename 含 ../ 写入上传目录之外。
            String safeName = sanitizeFileName(original);
            String prefix = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String uniqueName = prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
            Path target = dir.resolve(uniqueName).normalize();
            // 二次校验：确保最终路径仍在上传目录内
            if (!target.startsWith(dir)) {
                throw new BusinessException("非法的文件路径");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new BusinessException("文件保存失败：" + e.getMessage(), e);
        }
    }

    /**
     * 清理文件名，移除路径分隔符与上级目录引用，仅保留纯文件名。
     * 例如 {@code ../../etc/passwd} → {@code passwd}，{@code a\b.txt} → {@code b.txt}。
     */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        // 统一替换 Windows/Unix 路径分隔符为 /，再取最后一段
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // 积极防御：移除残留的 ".." 序列
        name = name.replace("..", "");
        if (name.isBlank()) {
            return "file";
        }
        return name;
    }

    @Override
    public InputStream load(String path) {
        if (path == null || path.isBlank()) {
            throw new BusinessException("文件路径为空");
        }
        try {
            return Files.newInputStream(Paths.get(path));
        } catch (IOException e) {
            throw new BusinessException("文件读取失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException e) {
            log.warn("文件删除失败: path={}, error={}", path, e.getMessage());
        }
    }

    @Override
    public Path resolveLocalPath(String path) {
        return Paths.get(path);
    }
}
