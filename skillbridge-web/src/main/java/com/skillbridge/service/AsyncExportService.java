package com.skillbridge.service;

import com.skillbridge.model.Activity;
import com.skillbridge.model.ExportTask;
import com.skillbridge.model.ExportTaskStatus;
import com.skillbridge.model.ExportType;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.ExportTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 异步导出服务。
 * <p>
 * 将耗时的批量导出（ZIP/Excel）放到后台线程执行，
 * 导出完成后将文件保存到临时目录，并更新任务状态。
 */
@Service
public class AsyncExportService {

    private static final Logger log = LoggerFactory.getLogger(AsyncExportService.class);

    private final ExportTaskRepository exportTaskRepository;
    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    /**
     * 自身代理引用。用于在 {@link #retryFailedTask} 中调用 {@link #executeZipExport} /
     * {@link #executeExcelExport} 时走 Spring 代理，确保 {@code @Async} 生效。
     * 直接同类自调用会绕过 AOP 代理，导致异步方法同步执行（阻塞请求线程）。
     */
    private final AsyncExportService self;

    @Value("${app.export-temp-dir:${java.io.tmpdir}/skillbridge-exports}")
    private String exportTempDir;

    public AsyncExportService(ExportTaskRepository exportTaskRepository,
                              ActivityRepository activityRepository,
                              ActivityService activityService,
                              @Lazy AsyncExportService self) {
        this.exportTaskRepository = exportTaskRepository;
        this.activityRepository = activityRepository;
        this.activityService = activityService;
        this.self = self;
    }

    /**
     * 创建导出任务记录（同步，在请求线程中执行）。
     */
    public ExportTask createTask(String type, Long activityId, String operator) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        ExportTask task = new ExportTask();
        task.setType(type);
        task.setActivityId(activityId);
        task.setActivityName(activity != null ? activity.getName() : String.valueOf(activityId));
        task.setOperator(operator);
        task.setStatus(ExportTaskStatus.PENDING.name());
        return exportTaskRepository.save(task);
    }

    /**
     * 异步执行 ZIP 导出。
     *
     * @param taskId     任务 ID
     * @param activityId 活动 ID
     */
    @Async("exportExecutor")
    public void executeZipExport(Long taskId, Long activityId) {
        executeExport(taskId, activityId, ExportType.ZIP, () -> {
            String fileName = "export_" + taskId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".zip";
            Path filePath = ensureExportDir().resolve(fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                activityService.exportAllAsZip(activityId, fos);
            }
            return new ExportResult(filePath.toString(), buildZipFileName(taskId, activityId));
        });
    }

    /**
     * 异步执行 Excel 导出。
     *
     * @param taskId     任务 ID
     * @param activityId 活动 ID
     */
    @Async("exportExecutor")
    public void executeExcelExport(Long taskId, Long activityId) {
        executeExport(taskId, activityId, ExportType.EXCEL, () -> {
            String fileName = "export_" + taskId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".xlsx";
            Path filePath = ensureExportDir().resolve(fileName);
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                activityService.exportExcel(activityId, fos);
            }
            return new ExportResult(filePath.toString(), buildExcelFileName(taskId, activityId));
        });
    }

    /**
     * 查询任务状态。
     */
    public ExportTask getTask(Long taskId) {
        return exportTaskRepository.findById(taskId).orElse(null);
    }

    /**
     * 查询操作人的所有导出任务。
     */
    public java.util.List<ExportTask> getTasksByOperator(String operator) {
        return exportTaskRepository.findByOperatorOrderByCreatedAtDesc(operator);
    }

    /**
     * 重试失败的导出任务。
     * <p>
     * 仅允许重试状态为 FAILED 的任务。重试时复用原任务的 type 与 activityId，
     * 创建新任务并异步执行，原任务保留不变（作为历史记录）。
     *
     * @param taskId 原失败任务 ID
     * @param operator 操作人（用于鉴权）
     * @return 新创建的重试任务
     */
    public ExportTask retryFailedTask(Long taskId, String operator) {
        ExportTask original = exportTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("导出任务不存在: " + taskId));
        // 鉴权：仅任务发起人可重试
        if (original.getOperator() != null && !original.getOperator().equals(operator)) {
            throw new BusinessException("无权重试他人的导出任务");
        }
        // 仅失败任务可重试
        if (!ExportTaskStatus.FAILED.name().equals(original.getStatus())) {
            throw new BusinessException("仅失败任务可重试，当前状态: " + original.getStatus());
        }
        // 创建新任务并执行（通过 self 代理调用，确保 @Async 生效）
        ExportTask retry = createTask(original.getType(), original.getActivityId(), operator);
        if (ExportType.ZIP.name().equals(original.getType())) {
            self.executeZipExport(retry.getId(), original.getActivityId());
        } else if (ExportType.EXCEL.name().equals(original.getType())) {
            self.executeExcelExport(retry.getId(), original.getActivityId());
        } else {
            throw new BusinessException("未知的导出类型: " + original.getType());
        }
        return retry;
    }

    // ============ 私有辅助 ============

    private void executeExport(Long taskId, Long activityId, ExportType type, ExportSupplier supplier) {
        ExportTask task = exportTaskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.warn("导出任务不存在: {}", taskId);
            return;
        }
        try {
            task.setStatus(ExportTaskStatus.RUNNING.name());
            exportTaskRepository.save(task);

            ExportResult result = supplier.get();
            task.setResultFilePath(result.filePath());
            task.setResultFileName(result.fileName());
            task.setStatus(ExportTaskStatus.SUCCESS.name());
            task.setCompletedAt(LocalDateTime.now());
            exportTaskRepository.save(task);
            log.info("导出任务 {} 完成，文件: {}", taskId, result.filePath());
        } catch (Exception e) {
            task.setStatus(ExportTaskStatus.FAILED.name());
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
            exportTaskRepository.save(task);
            log.error("导出任务 {} 失败: {}", taskId, e.getMessage(), e);
        }
    }

    private Path ensureExportDir() throws IOException {
        Path dir = Paths.get(exportTempDir);
        Files.createDirectories(dir);
        return dir;
    }

    private String buildZipFileName(Long taskId, Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        String name = activity != null ? activity.getName() : String.valueOf(activityId);
        return "【" + name + "】全员申报表汇总.zip";
    }

    private String buildExcelFileName(Long taskId, Long activityId) {
        Activity activity = activityRepository.findById(activityId).orElse(null);
        String name = activity != null ? activity.getName() : String.valueOf(activityId);
        return "【" + name + "】学生提交数据.xlsx";
    }

    @FunctionalInterface
    private interface ExportSupplier {
        ExportResult get() throws Exception;
    }

    private record ExportResult(String filePath, String fileName) {}
}
