package com.skillbridge.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * 文件存储服务抽象接口。
 * <p>
 * 支持本地文件系统与云存储（如 S3、OSS）切换。
 * 实现类通过 {@code @Primary} 或配置条件注入。
 */
public interface FileStorageService {

    /**
     * 保存上传的文件，返回可存储的相对或绝对路径标识。
     *
     * @param file 上传的文件
     * @return 存储路径标识（用于后续读取/删除）
     */
    String store(MultipartFile file);

    /**
     * 根据存储路径标识读取文件字节流。
     *
     * @param path 存储路径标识
     * @return 文件输入流
     */
    InputStream load(String path);

    /**
     * 根据存储路径标识删除文件。
     * 文件不存在时静默返回，不抛异常。
     *
     * @param path 存储路径标识
     */
    void delete(String path);

    /**
     * 根据存储路径标识获取本地文件 Path（用于 Python 微服务调用等需要本地路径的场景）。
     * 云存储实现可抛出 UnsupportedOperationException。
     *
     * @param path 存储路径标识
     * @return 本地文件 Path
     */
    Path resolveLocalPath(String path);
}
