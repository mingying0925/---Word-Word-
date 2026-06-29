package com.skillbridge.controller;

import com.skillbridge.model.Template;
import com.skillbridge.service.AuditLogService;
import com.skillbridge.service.BusinessException;
import com.skillbridge.service.TemplateService;
import com.skillbridge.service.ActivityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * 模板库控制器。
 * <p>
 * 管理教师可复用的 Word 模板库：查看、从活动保存、上传新模板、删除。
 */
@Controller
@RequestMapping("/teacher/templates")
@Validated
public class TemplateController {

    private final TemplateService templateService;
    private final AuditLogService auditLogService;
    private final MessageSource messageSource;
    private final ActivityService activityService;

    public TemplateController(TemplateService templateService,
                              AuditLogService auditLogService,
                              MessageSource messageSource,
                              ActivityService activityService) {
        this.templateService = templateService;
        this.auditLogService = auditLogService;
        this.messageSource = messageSource;
        this.activityService = activityService;
    }

    /** 模板库列表页（仅展示当前教师的模板） */
    @GetMapping
    public String listTemplates(HttpServletRequest request, Model model) {
        List<Template> templates = templateService.findByOwner(getCurrentOperator(request));
        model.addAttribute("templates", templates);
        return "teacher/template-library";
    }

    /** 从已有活动保存模板到库 */
    @PostMapping("/save-from-activity")
    public String saveFromActivity(@RequestParam("activityId") Long activityId,
                                   @RequestParam("name") @NotBlank @Size(max = 100, message = "模板名称长度不能超过 100") String name,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        try {
            Template template = templateService.saveFromActivity(activityId, name, getCurrentOperator(request));
            auditLogService.record(getCurrentOperator(request), "teacher", "SAVE_TEMPLATE",
                    "template", String.valueOf(template.getId()),
                    "保存模板到库：" + name, getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("template.save.success", new Object[]{name}, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/templates";
    }

    /** 上传新模板到库 */
    @PostMapping("/upload")
    public String uploadTemplate(@RequestParam("name") @NotBlank @Size(max = 100, message = "模板名称长度不能超过 100") String name,
                                 @RequestParam("file") MultipartFile file,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            Template template = templateService.saveFromUpload(name, file, getCurrentOperator(request));
            auditLogService.record(getCurrentOperator(request), "teacher", "UPLOAD_TEMPLATE",
                    "template", String.valueOf(template.getId()),
                    "上传模板到库：" + name, getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("template.upload.success", new Object[]{name}, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/templates";
    }

    /** 删除模板 */
    @PostMapping("/delete")
    public String deleteTemplate(@RequestParam("id") Long id,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            templateService.delete(id, getCurrentOperator(request));
            auditLogService.record(getCurrentOperator(request), "teacher", "DELETE_TEMPLATE",
                    "template", String.valueOf(id),
                    "删除模板 ID: " + id, getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("template.delete.success", null, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/templates";
    }

    /**
     * 预览模板字段（P1-7）。
     * <p>
     * 解析模板的 bookmarksJson，展示字段列表与表格结构，便于教师在创建活动前确认模板内容。
     * 复用 confirm-fields 页面的展示逻辑，但不允许编辑。
     */
    @GetMapping("/{id}/preview")
    public String previewTemplate(@PathVariable Long id,
                                  HttpServletRequest request,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            Template template = templateService.findById(id)
                    .orElseThrow(() -> new BusinessException("模板不存在: " + id));
            templateService.assertOwner(template, getCurrentOperator(request));
            // 复用 ActivityService 的 JSON 解析逻辑
            Map<String, Object> structure = activityService.parseStructureFromJson(template.getBookmarksJson());
            model.addAllAttributes(structure);
            model.addAttribute("template", template);
            model.addAttribute("previewMode", true);
            return "teacher/template-preview";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/teacher/templates";
        }
    }

    // ============ 私有辅助 ============

    private String getCurrentOperator(HttpServletRequest request) {
        return ControllerHelper.getCurrentOperator(request);
    }

    private String getClientIp(HttpServletRequest request) {
        return ControllerHelper.getClientIp(request);
    }
}
