package com.skillbridge.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Python 导出微服务调用客户端。
 * 调用 Flask 服务（默认 http://localhost:5000/api）：
 *   - POST /api/parse-bookmarks  上传 .docx 文件，返回书签解析 JSON
 *   - POST /api/fill-word        传入 template_path + data，返回填好的 .docx 字节流
 * <p>
 * 容错机制：
 * <ul>
 *   <li>重试：调用失败时自动重试，最多 {@code app.export.retry-max} 次（默认 2），指数退避</li>
 *   <li>熔断：连续失败超过阈值时进入熔断状态，直接快速失败，定期探测恢复</li>
 * </ul>
 */
@Service
public class PythonExportClient {

    private static final Logger log = LoggerFactory.getLogger(PythonExportClient.class);

    private final RestTemplate restTemplate;

    @Value("${export.service.url}")
    private String exportServiceUrl;

    @Value("${app.export.retry-max:2}")
    private int retryMax;

    @Value("${app.export.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    /** 熔断器：连续失败次数 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    /** 熔断阈值：连续失败超过此值则熔断 */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    /** 熔断恢复探测间隔（毫秒） */
    private static final long CIRCUIT_BREAKER_RECOVERY_MS = 30_000;
    /** 上次失败时间（用于判断是否可以探测恢复） */
    private volatile long lastFailureTime = 0;

    public PythonExportClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 解析 Word 模板书签。
     * 将 MultipartFile 临时保存到本地，再以 FileSystemResource 上传给 Python 服务。
     *
     * @param file 上传的 .docx 文件
     * @return Python 返回的 JSON 字符串
     */
    public String parseBookmarks(MultipartFile file) {
        Path temp = null;
        try {
            String original = file.getOriginalFilename();
            String suffix = (original != null && original.contains("."))
                    ? original.substring(original.lastIndexOf('.'))
                    : ".docx";
            temp = Files.createTempFile("skillbridge-upload-", suffix);
            file.transferTo(temp.toFile());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(temp.toFile()));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String url = exportServiceUrl + "/parse-bookmarks";
            ResponseEntity<String> resp = executeWithRetry(() ->
                    restTemplate.postForEntity(url, requestEntity, String.class), "parseBookmarks");
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new BusinessException("Python 书签解析失败，HTTP " + resp.getStatusCode());
            }
            return resp.getBody();
        } catch (IOException e) {
            throw new BusinessException("上传文件临时保存失败：" + e.getMessage(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    log.warn("临时文件删除失败: path={}", temp);
                }
            }
        }
    }

    /**
     * 调用 Python 服务填充 Word 模板，返回生成的 .docx 字节流。
     * 将模板文件以 multipart 形式上传，不再依赖共享文件系统路径。
     *
     * @param templatePath 模板文件绝对路径
     * @param data         书签名 -> 值
     * @return 生成的 .docx 文件字节
     */
    public byte[] fillWord(String templatePath, Map<String, String> data) {
        String url = exportServiceUrl + "/fill-word";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("template", new FileSystemResource(templatePath));
        body.add("data", toJsonString(data));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<byte[]> resp = executeWithRetry(() ->
                restTemplate.postForEntity(url, requestEntity, byte[].class), "fillWord");
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new BusinessException("Python Word 填充失败，HTTP " + resp.getStatusCode());
        }
        return resp.getBody();
    }

    /**
     * 检查 Python 服务是否可用（轻量级健康探测）。
     */
    public boolean isAvailable() {
        try {
            restTemplate.getForEntity(exportServiceUrl + "/health", String.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ============ 容错机制 ============

    /**
     * 带重试与熔断的请求执行器。
     *
     * @param supplier 实际 HTTP 调用
     * @param operation 操作名称（用于日志）
     * @return 响应实体
     * @param <T> 响应类型
     */
    private <T> ResponseEntity<T> executeWithRetry(java.util.function.Supplier<ResponseEntity<T>> supplier,
                                                    String operation) {
        // 熔断检查
        if (isCircuitOpen()) {
            throw new BusinessException("Python 导出服务熔断中，请稍后重试。连续失败次数: "
                    + consecutiveFailures.get());
        }

        RestClientException lastException = null;
        for (int attempt = 0; attempt <= retryMax; attempt++) {
            try {
                ResponseEntity<T> resp = supplier.get();
                // 成功则重置熔断器
                onSuccess();
                return resp;
            } catch (RestClientException e) {
                lastException = e;
                if (attempt < retryMax) {
                    long backoff = retryBackoffMs * (1L << attempt); // 指数退避
                    log.warn("Python 服务调用失败（{}），第 {} 次重试，等待 {}ms：{}",
                            operation, attempt + 1, backoff, e.getMessage());
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException("调用被中断", ie);
                    }
                }
            }
        }
        // 全部重试失败
        onFailure();
        throw new BusinessException("Python " + operation + " 服务不可用（已重试 "
                + retryMax + " 次）：" + lastException.getMessage(), lastException);
    }

    /** 判断熔断器是否开启 */
    private boolean isCircuitOpen() {
        if (consecutiveFailures.get() < CIRCUIT_BREAKER_THRESHOLD) {
            return false;
        }
        // 超过恢复间隔后允许探测
        long elapsed = System.currentTimeMillis() - lastFailureTime;
        if (elapsed > CIRCUIT_BREAKER_RECOVERY_MS) {
            log.info("熔断器进入半开状态，允许探测请求");
            return false;
        }
        return true;
    }

    /** 调用成功：重置失败计数 */
    private void onSuccess() {
        if (consecutiveFailures.get() > 0) {
            log.info("Python 服务调用恢复，重置熔断器");
            consecutiveFailures.set(0);
        }
    }

    /** 调用失败：递增失败计数 */
    private void onFailure() {
        int count = consecutiveFailures.incrementAndGet();
        lastFailureTime = System.currentTimeMillis();
        if (count >= CIRCUIT_BREAKER_THRESHOLD) {
            log.error("Python 服务连续失败 {} 次，触发熔断", count);
        }
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private String toJsonString(Map<String, String> data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new BusinessException("数据序列化失败：" + e.getMessage(), e);
        }
    }
}
