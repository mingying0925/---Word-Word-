package com.skillbridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.model.Activity;
import com.skillbridge.model.Template;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.TemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

/**
 * 模板库服务。
 * <p>
 * 教师可将常用 Word 模板保存到库中，创建活动时可直接从库中选择复用。
 * 支持从已有活动保存模板，或直接上传新模板到库中。
 */
@Service
public class TemplateService {

    private final TemplateRepository templateRepository;
    private final ActivityRepository activityRepository;
    private final PythonExportClient pythonExportClient;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public TemplateService(TemplateRepository templateRepository,
                           ActivityRepository activityRepository,
                           PythonExportClient pythonExportClient,
                           FileStorageService fileStorageService,
                           ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.activityRepository = activityRepository;
        this.pythonExportClient = pythonExportClient;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /** 查询所有模板（按创建时间倒序） */
    public List<Template> findAll() {
        return templateRepository.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    /** 按归属教师查询模板 */
    public List<Template> findByOwner(String ownerId) {
        return templateRepository.findByOwnerId(ownerId);
    }

    /** 按 ID 查询模板 */
    public Optional<Template> findById(Long id) {
        return templateRepository.findById(id);
    }

    /**
     * 校验当前教师是否为指定模板的归属人。
     * 历史数据（ownerId 为 null）跳过校验保持向后兼容。
     */
    public void assertOwner(Template template, String ownerId) {
        if (template == null) {
            throw new BusinessException("模板不存在");
        }
        if (template.getOwnerId() != null && !template.getOwnerId().equals(ownerId)) {
            throw new BusinessException("无权操作他人创建的模板");
        }
    }

    /**
     * 从模板库准备待确认的模板数据（复制文件 + 返回 bookmarksJson）。
     * <p>
     * 用于创建活动时从模板库选择模板：复制模板文件到新路径，避免活动删除时影响模板库。
     *
     * @param templateId 模板库 ID
     * @return 待确认的模板数据（templatePath + bookmarksJson）
     */
    public ActivityService.PendingTemplate preparePendingFromLibrary(Long templateId) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException("模板不存在: " + templateId));
        if (template.getTemplatePath() == null || template.getBookmarksJson() == null) {
            throw new BusinessException("该模板数据不完整，无法使用");
        }
        String newPath = copyTemplateFile(template.getTemplatePath());
        return new ActivityService.PendingTemplate(newPath, template.getBookmarksJson());
    }

    /**
     * 从已有活动保存模板到库。
     * <p>
     * 复制活动的模板文件和书签 JSON 到模板库，创建新的 Template 实体。
     *
     * @param activityId 源活动 ID
     * @param name       模板名称
     * @return 已保存的模板
     */
    @Transactional
    public Template saveFromActivity(Long activityId, String name, String ownerId) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("模板名称不能为空");
        }
        name = name.trim();
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在: " + activityId));
        if (activity.getTemplatePath() == null || activity.getBookmarksJson() == null) {
            throw new BusinessException("该活动没有模板数据，无法保存到库");
        }
        // 复制模板文件到新路径（避免活动删除时模板文件被清理）
        String newPath = copyTemplateFile(activity.getTemplatePath());
        Template template = new Template();
        template.setName(name);
        template.setOwnerId(ownerId);
        template.setTemplatePath(newPath);
        template.setBookmarksJson(activity.getBookmarksJson());
        template.setFieldCount(countFields(activity.getBookmarksJson()));
        return templateRepository.save(template);
    }

    /**
     * 上传新模板到库（解析书签后保存）。
     *
     * @param name     模板名称
     * @param file     Word 模板文件（.docx）
     * @param ownerId  创建教师工号（归属校验用）
     * @return 已保存的模板
     */
    @Transactional
    public Template saveFromUpload(String name, MultipartFile file, String ownerId) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("模板名称不能为空");
        }
        name = name.trim();
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Word 模板文件");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".docx")) {
            throw new BusinessException("仅支持 .docx 模板文件");
        }
        // 保存文件
        String storedPath = fileStorageService.store(file);
        // 解析书签
        String bookmarksJson;
        try {
            bookmarksJson = pythonExportClient.parseBookmarks(file);
        } catch (BusinessException e) {
            // 解析失败时清理已保存文件
            fileStorageService.delete(storedPath);
            throw e;
        }
        Template template = new Template();
        template.setName(name);
        template.setOwnerId(ownerId);
        template.setTemplatePath(storedPath);
        template.setBookmarksJson(bookmarksJson);
        template.setFieldCount(countFields(bookmarksJson));
        return templateRepository.save(template);
    }

    /** 删除模板及其文件 */
    @Transactional
    public void delete(Long id, String ownerId) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new BusinessException("模板不存在: " + id));
        assertOwner(template, ownerId);
        if (template.getTemplatePath() != null) {
            fileStorageService.delete(template.getTemplatePath());
        }
        templateRepository.delete(template);
    }

    /**
     * 统计 bookmarksJson 中的书签字段数量。
     */
    private int countFields(String bookmarksJson) {
        if (bookmarksJson == null || bookmarksJson.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(bookmarksJson);
            JsonNode bookmarks = root.path("bookmarks");
            return bookmarks.isArray() ? bookmarks.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 复制模板文件到新路径（在原路径同目录下生成带时间戳的副本）。
     */
    private String copyTemplateFile(String sourcePath) {
        Path source = fileStorageService.resolveLocalPath(sourcePath);
        if (!Files.exists(source)) {
            throw new BusinessException("源模板文件不存在: " + sourcePath);
        }
        String timestamp = String.valueOf(System.currentTimeMillis());
        String originalName = source.getFileName().toString();
        String newName = timestamp + "_" + originalName;
        Path target = source.getParent().resolve(newName);
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new BusinessException("复制模板文件失败: " + e.getMessage(), e);
        }
        return target.toAbsolutePath().toString();
    }
}
