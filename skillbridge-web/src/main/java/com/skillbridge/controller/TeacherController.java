package com.skillbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.model.Activity;
import com.skillbridge.model.Submission;
import com.skillbridge.model.Teacher;
import com.skillbridge.service.ActivityService;
import com.skillbridge.service.AsyncExportService;
import com.skillbridge.service.AuditLogService;
import com.skillbridge.service.BusinessException;
import com.skillbridge.service.DashboardService;
import com.skillbridge.service.StudentRosterService;
import com.skillbridge.service.TeacherService;
import com.skillbridge.service.TemplateService;
import com.skillbridge.utils.CookieHelper;
import com.skillbridge.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/teacher")
@Validated
public class TeacherController {

    private final ActivityService activityService;
    private final TeacherService teacherService;
    private final DashboardService dashboardService;
    private final AuditLogService auditLogService;
    private final AsyncExportService asyncExportService;
    private final TemplateService templateService;
    private final StudentRosterService rosterService;
    private final JwtUtil jwtUtil;
    private final CookieHelper cookieHelper;
    private final MessageSource messageSource;
    private static final Logger log = LoggerFactory.getLogger(TeacherController.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TeacherController(ActivityService activityService,
                             TeacherService teacherService,
                             DashboardService dashboardService,
                             AuditLogService auditLogService,
                             AsyncExportService asyncExportService,
                             TemplateService templateService,
                             StudentRosterService rosterService,
                             JwtUtil jwtUtil,
                             CookieHelper cookieHelper,
                             MessageSource messageSource) {
        this.activityService = activityService;
        this.teacherService = teacherService;
        this.dashboardService = dashboardService;
        this.auditLogService = auditLogService;
        this.asyncExportService = asyncExportService;
        this.templateService = templateService;
        this.rosterService = rosterService;
        this.jwtUtil = jwtUtil;
        this.cookieHelper = cookieHelper;
        this.messageSource = messageSource;
    }

    // ============ 双入口：教师登录 ============
    /** 教师专属登录页（教工号 + 密码） */
    @GetMapping("/login")
    public String showTeacherLogin(@RequestParam(value = "error", required = false) String error,
                                   HttpServletRequest request,
                                   Model model) {
        if (error != null) {
            model.addAttribute("error", mapError(error, request));
        }
        return "teacher/login";
    }

    /** 教师登录提交 → 数据库 BCrypt 校验 → 生成 teacher 角色 JWT → 写 Cookie → 跳活动列表 */
    @PostMapping("/login")
    public String handleTeacherLogin(@RequestParam("teacherId") String teacherId,
                                     @RequestParam("password") String password,
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     RedirectAttributes redirectAttributes) {
        // 教师工号格式校验：字母或数字，2-20 位
        if (teacherId == null || !teacherId.matches("^[a-zA-Z0-9]{2,20}$")) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("teacher.login.error.invalidId", null, request.getLocale()));
            return "redirect:/teacher/login";
        }
        // 密码非空校验
        if (password == null || password.length() < 6) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("teacher.login.error.shortPassword", null, request.getLocale()));
            return "redirect:/teacher/login";
        }
        // 真实认证：数据库 BCrypt 比对
        Optional<Teacher> teacher = teacherService.login(teacherId, password);
        if (teacher.isEmpty()) {
            auditLogService.record(teacherId, "teacher", "LOGIN_FAILED",
                    "teacher", null, "登录失败：工号或密码错误", getClientIp(request));
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("teacher.login.error.wrongCredential", null, request.getLocale()));
            return "redirect:/teacher/login";
        }
        String token = jwtUtil.generateToken(teacherId, "teacher");
        cookieHelper.writeTokenCookie(response, token);
        auditLogService.record(teacherId, "teacher", "LOGIN",
                "teacher", teacherId, "登录成功", getClientIp(request));
        return "redirect:/teacher/dashboard";
    }

    /** 首页重定向到仪表盘 */
    @GetMapping("")
    public String index() {
        return "redirect:/teacher/dashboard";
    }

    /** 1. 创建活动页面 */
    @GetMapping("/create")
    public String showCreatePage(Model model) {
        model.addAttribute("activity", new Activity());
        // 提供模板库列表，供教师直接选择复用
        model.addAttribute("availableTemplates", templateService.findAll());
        return "teacher/create-activity";
    }

    /**
     * 2. 处理上传：解析书签 → 保存为草稿活动 → 跳转确认页（PRD F1/F2）
     * <p>
     * 使用数据库草稿（status=2）替代 HttpSession 暂存，支持集群部署。
     * 支持两种来源：
     * <ul>
     *   <li>templateId 非空：从模板库复制模板文件与 bookmarksJson</li>
     *   <li>templateFile 上传：解析新上传的 Word 模板</li>
     * </ul>
     */
    @PostMapping("/create")
    public String handleCreate(@RequestParam("name") String name,
                               @RequestParam(value = "deadline", required = false) String deadline,
                               @RequestParam(value = "templateId", required = false) Long templateId,
                               @RequestParam(value = "templateFile", required = false) MultipartFile templateFile,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        try {
            LocalDateTime deadlineDt = null;
            if (deadline != null && !deadline.isBlank()) {
                deadlineDt = LocalDateTime.parse(deadline,
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
            ActivityService.PendingTemplate pending;
            if (templateId != null) {
                // 从模板库创建：复制模板文件与 bookmarksJson
                pending = templateService.preparePendingFromLibrary(templateId);
            } else {
                // 上传新模板：解析书签
                if (templateFile == null || templateFile.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("teacher.create.error.noTemplate", null, request.getLocale()));
                    redirectAttributes.addFlashAttribute("draftName", name);
                    redirectAttributes.addFlashAttribute("draftDeadline", deadline);
                    return "redirect:/teacher/create";
                }
                pending = activityService.parseTemplate(templateFile);
            }
            // 保存为草稿活动（status=2），返回 draftId
            Activity draft = activityService.saveDraft(name, deadlineDt,
                    pending.templatePath(), pending.bookmarksJson(), getCurrentOperator(request));
            return "redirect:/teacher/create/confirm?draftId=" + draft.getId();
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("draftName", name);
            redirectAttributes.addFlashAttribute("draftDeadline", deadline);
            return "redirect:/teacher/create";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("teacher.create.error.failed", null, request.getLocale()));
            redirectAttributes.addFlashAttribute("draftName", name);
            redirectAttributes.addFlashAttribute("draftDeadline", deadline);
            return "redirect:/teacher/create";
        }
    }

    /** 3. 确认页：通过 draftId 从数据库加载草稿，展示解析出的字段列表与表格结构 */
    @GetMapping("/create/confirm")
    public String showConfirm(@RequestParam("draftId") Long draftId,
                              HttpServletRequest request,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        try {
            Activity draft = activityService.getDraftById(draftId, getCurrentOperator(request));
            Map<String, Object> structure = activityService.parseStructureFromJson(draft.getBookmarksJson());
            model.addAllAttributes(structure);
            model.addAttribute("draftId", draftId);
            model.addAttribute("pendingName", draft.getName());
            model.addAttribute("pendingDeadline", draft.getDeadline());
            return "teacher/confirm-fields";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/teacher/create";
        }
    }

    /** 4. 确认保存：先更新字段配置（如有），再将草稿活动转为正式活动（status 2→0） */
    @PostMapping("/create/confirm")
    public String handleConfirm(@RequestParam("draftId") Long draftId,
                                @RequestParam(value = "fieldConfigs", required = false) String fieldConfigs,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            String operator = getCurrentOperator(request);
            // 若教师编辑了字段配置，先更新草稿的 bookmarksJson
            if (fieldConfigs != null && !fieldConfigs.isBlank()) {
                activityService.updateDraftFields(draftId, fieldConfigs, operator);
            }
            Activity activity = activityService.confirmDraft(draftId, operator);
            auditLogService.record(getCurrentOperator(request), "teacher", "CREATE_ACTIVITY",
                    "activity", String.valueOf(activity.getId()),
                    "创建活动：" + activity.getName(), getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.create.msg.success",
                            new Object[]{activity.getName()}, request.getLocale()));
            return "redirect:/teacher/activities";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/teacher/create";
        }
    }

    /** 5. 取消创建：删除草稿活动及其模板文件 */
    @PostMapping("/create/cancel")
    public String cancelCreate(@RequestParam(value = "draftId", required = false) Long draftId,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        if (draftId != null) {
            try {
                activityService.deleteDraft(draftId, getCurrentOperator(request));
            } catch (BusinessException e) {
                log.warn("删除草稿失败（可能已删除）: {}", e.getMessage());
            }
        }
        redirectAttributes.addFlashAttribute("msg",
                messageSource.getMessage("teacher.create.msg.cancelled", null, request.getLocale()));
        return "redirect:/teacher/create";
    }

    /** 仪表盘页面：展示活动数、提交数、今日新增等统计指标 */
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAllAttributes(dashboardService.getDashboardStats());
        return "teacher/dashboard";
    }

    /** 3. 活动列表页面（支持按名称/状态搜索筛选，仅展示当前教师创建的活动） */
    @GetMapping("/activities")
    public String listActivities(@RequestParam(defaultValue = "0") @Min(0) int page,
                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                 @RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam(value = "status", required = false) Integer status,
                                 HttpServletRequest request,
                                 Model model) {
        String operator = getCurrentOperator(request);
        Page<Activity> activityPage = activityService.searchActivitiesByOwner(operator, keyword, status, page, size);
        List<Activity> activities = activityPage.getContent();
        // 计算即将截止的活动 ID 集合（24 小时内截止且仍在报名中）
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime urgentThreshold = now.plusHours(24);
        // 批量获取活动 ID 列表
        java.util.List<Long> ids = activities.stream().map(Activity::getId).collect(java.util.stream.Collectors.toList());
        // 批量查询提交人数和名单人数（2 次查询替代 N+1）
        java.util.Map<Long, Long> submissionCounts = activityService.countSubmissionsByActivityIds(ids);
        java.util.Map<Long, Long> rosterCounts = rosterService.countByActivityIds(ids);
        java.util.Set<Long> urgentIds = new java.util.HashSet<>();
        for (Activity a : activities) {
            if (a.getStatus() != null && a.getStatus() == 0
                    && a.getDeadline() != null
                    && a.getDeadline().isAfter(now)
                    && a.getDeadline().isBefore(urgentThreshold)) {
                urgentIds.add(a.getId());
            }
        }
        model.addAttribute("activities", activityPage.getContent());
        model.addAttribute("totalPages", activityPage.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNext", activityPage.hasNext());
        model.addAttribute("hasPrevious", activityPage.hasPrevious());
        // 回显搜索条件
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("status", status);
        model.addAttribute("urgentIds", urgentIds);
        model.addAttribute("submissionCounts", submissionCounts);
        model.addAttribute("rosterCounts", rosterCounts);
        // 统计概览
        model.addAttribute("totalCount", activityPage.getTotalElements());
        return "teacher/activity-list";
    }

    /** 截止活动报名（status 0→1） */
    @PostMapping("/activity/{id}/close")
    public String closeActivity(@PathVariable Long id,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            Activity activity = activityService.closeActivity(id, getCurrentOperator(request));
            auditLogService.record(getCurrentOperator(request), "teacher", "CLOSE_ACTIVITY",
                    "activity", String.valueOf(id),
                    "截止活动：" + activity.getName(), getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.activity.msg.closed", null, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/activities";
    }

    /** 删除活动（级联删除提交记录与模板文件） */
    @PostMapping("/activity/{id}/delete")
    public String deleteActivity(@PathVariable Long id,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        try {
            Activity activity = activityService.getActivityById(id).orElse(null);
            String activityName = activity != null ? activity.getName() : String.valueOf(id);
            activityService.deleteActivity(id, getCurrentOperator(request));
            auditLogService.record(getCurrentOperator(request), "teacher", "DELETE_ACTIVITY",
                    "activity", String.valueOf(id),
                    "删除活动：" + activityName, getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.activity.msg.deleted", null, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/activities";
    }

    /** 4. 活动详情 → 查看提交列表 */
    @GetMapping("/activity/{id}/submissions")
    public String viewSubmissions(@PathVariable Long id, HttpServletRequest request, Model model) {
        Activity activity = activityService.getActivityById(id)
                .orElseThrow(() -> new BusinessException(messageSource.getMessage("teacher.activity.error.notFound",
                        new Object[]{id}, request.getLocale())));
        List<Submission> submissions = activityService.getSubmissionsByActivityId(id);
        model.addAttribute("activity", activity);
        model.addAttribute("submissions", submissions);
        return "teacher/submissions";
    }

    /** 5. 导出 Word 接口 → 返回文件下载 */
    @GetMapping("/export/{submissionId}")
    public ResponseEntity<byte[]> exportWord(@PathVariable Long submissionId,
                                             HttpServletRequest request) {
        byte[] data = activityService.exportWord(submissionId);
        String fileName = activityService.buildExportFileName(submissionId);
        auditLogService.record(getCurrentOperator(request), "teacher", "EXPORT_WORD",
                "submission", String.valueOf(submissionId),
                "导出 Word：" + fileName, getClientIp(request));
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, org.springframework.http.HttpStatus.OK);
    }

    /** 5.1 一键批量导出某活动下所有学生申报表为 ZIP 包 */
    @GetMapping("/activity/{activityId}/export/zip")
    public void exportZip(@PathVariable Long activityId,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        // 先取活动名称用于压缩包命名；若活动不存在则直接返回 JSON 错误
        Activity activity = activityService.getActivityById(activityId).orElse(null);
        if (activity == null) {
            writeJsonError(response, messageSource.getMessage("teacher.activity.error.notFound",
                    new Object[]{activityId}, request.getLocale()));
            return;
        }

        // 预设 ZIP 响应头（此时尚未写入正文，响应未提交，仍可 reset）
        String zipName = "【" + activity.getName() + "】全员申报表汇总.zip";
        String encoded = URLEncoder.encode(zipName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/zip");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + zipName + "\"; filename*=UTF-8''" + encoded);

        try {
            activityService.exportAllAsZip(activityId, response.getOutputStream());
            auditLogService.record(getCurrentOperator(request), "teacher", "EXPORT_ZIP",
                    "activity", String.valueOf(activityId),
                    "批量导出 ZIP：" + activity.getName(), getClientIp(request));
        } catch (BusinessException e) {
            if (!response.isCommitted()) {
                response.reset();
                writeJsonError(response, e.getMessage());
            }
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.reset();
                writeJsonError(response, messageSource.getMessage("teacher.export.error.batchFailed", null, request.getLocale()));
            }
        }
    }

    /** 以 JSON 形式向前端返回错误信息（供 Fetch 客户端解析） */
    private void writeJsonError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> errorBody = Map.of("success", false, "message", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        response.getWriter().flush();
    }

    /** 5.2 导出某活动所有学生提交数据为 Excel（.xlsx） */
    @GetMapping("/activity/{activityId}/export/excel")
    public void exportExcel(@PathVariable Long activityId,
                            HttpServletRequest request,
                            HttpServletResponse response) throws IOException {
        Activity activity = activityService.getActivityById(activityId).orElse(null);
        if (activity == null) {
            writeJsonError(response, messageSource.getMessage("teacher.activity.error.notFound",
                    new Object[]{activityId}, request.getLocale()));
            return;
        }

        String excelName = "【" + activity.getName() + "】学生提交数据.xlsx";
        String encoded = URLEncoder.encode(excelName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + excelName + "\"; filename*=UTF-8''" + encoded);

        try {
            activityService.exportExcel(activityId, response.getOutputStream());
            auditLogService.record(getCurrentOperator(request), "teacher", "EXPORT_EXCEL",
                    "activity", String.valueOf(activityId),
                    "导出 Excel：" + activity.getName(), getClientIp(request));
        } catch (BusinessException e) {
            if (!response.isCommitted()) {
                response.reset();
                writeJsonError(response, e.getMessage());
            }
        } catch (Exception e) {
            if (!response.isCommitted()) {
                response.reset();
                writeJsonError(response, messageSource.getMessage("teacher.export.error.excelFailed", null, request.getLocale()));
            }
        }
    }

    /** 5.3 异步导出 ZIP：创建导出任务，后台执行，返回任务 ID */
    @PostMapping("/activity/{activityId}/export/async/zip")
    public void asyncExportZip(@PathVariable Long activityId,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException {
        Activity activity = activityService.getActivityById(activityId).orElse(null);
        if (activity == null) {
            writeJsonError(response, messageSource.getMessage("teacher.activity.error.notFound",
                    new Object[]{activityId}, request.getLocale()));
            return;
        }
        com.skillbridge.model.ExportTask task = asyncExportService.createTask(
                "ZIP", activityId, getCurrentOperator(request));
        asyncExportService.executeZipExport(task.getId(), activityId);
        auditLogService.record(getCurrentOperator(request), "teacher", "EXPORT_ZIP_ASYNC",
                "activity", String.valueOf(activityId),
                "发起异步 ZIP 导出：" + activity.getName(), getClientIp(request));
        String msg = messageSource.getMessage("teacher.export.msg.taskCreated", null, request.getLocale());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("success", true, "taskId", task.getId(), "message", msg)));
    }

    /** 5.4 异步导出 Excel：创建导出任务，后台执行，返回任务 ID */
    @PostMapping("/activity/{activityId}/export/async/excel")
    public void asyncExportExcel(@PathVariable Long activityId,
                                 HttpServletRequest request,
                                 HttpServletResponse response) throws IOException {
        Activity activity = activityService.getActivityById(activityId).orElse(null);
        if (activity == null) {
            writeJsonError(response, messageSource.getMessage("teacher.activity.error.notFound",
                    new Object[]{activityId}, request.getLocale()));
            return;
        }
        com.skillbridge.model.ExportTask task = asyncExportService.createTask(
                "EXCEL", activityId, getCurrentOperator(request));
        asyncExportService.executeExcelExport(task.getId(), activityId);
        auditLogService.record(getCurrentOperator(request), "teacher", "EXPORT_EXCEL_ASYNC",
                "activity", String.valueOf(activityId),
                "发起异步 Excel 导出：" + activity.getName(), getClientIp(request));
        String msg = messageSource.getMessage("teacher.export.msg.taskCreated", null, request.getLocale());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("success", true, "taskId", task.getId(), "message", msg)));
    }

    /** 5.5 导出任务列表页面 */
    @GetMapping("/export-tasks")
    public String exportTasks(HttpServletRequest request, Model model) {
        model.addAttribute("tasks", asyncExportService.getTasksByOperator(getCurrentOperator(request)));
        return "teacher/export-tasks";
    }

    /** 5.6 下载已完成的导出文件 */
    @GetMapping("/export-tasks/{taskId}/download")
    public void downloadExportTask(@PathVariable Long taskId,
                                   HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {
        com.skillbridge.model.ExportTask task = asyncExportService.getTask(taskId);
        if (task == null) {
            writeJsonError(response, messageSource.getMessage("teacher.export.error.taskNotFound",
                    new Object[]{taskId}, request.getLocale()));
            return;
        }
        if (!"SUCCESS".equals(task.getStatus())) {
            writeJsonError(response, messageSource.getMessage("teacher.export.error.taskNotReady",
                    new Object[]{task.getStatus()}, request.getLocale()));
            return;
        }
        if (task.getResultFilePath() == null) {
            writeJsonError(response, messageSource.getMessage("teacher.export.error.filePathMissing",
                    null, request.getLocale()));
            return;
        }

        Path filePath = Paths.get(task.getResultFilePath());
        if (!Files.exists(filePath)) {
            writeJsonError(response, messageSource.getMessage("teacher.export.error.fileCleaned",
                    null, request.getLocale()));
            return;
        }

        String fileName = task.getResultFileName() != null ? task.getResultFileName() : filePath.getFileName().toString();
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/octet-stream");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        Files.copy(filePath, response.getOutputStream());
        response.getOutputStream().flush();
    }

    /** 5.7 重试失败的导出任务（创建新任务并异步执行） */
    @PostMapping("/export-tasks/{taskId}/retry")
    public String retryExportTask(@PathVariable Long taskId,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            com.skillbridge.model.ExportTask retry = asyncExportService.retryFailedTask(taskId, getCurrentOperator(request));
            auditLogService.record(getCurrentOperator(request), "teacher", "EXPORT_RETRY",
                    "export_task", String.valueOf(taskId),
                    "重试导出任务 #" + taskId + " → 新任务 #" + retry.getId(), getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.export.msg.taskCreated", null, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/export-tasks";
    }

    /** 6. 预览表单（复用学生端表单页面，通过 ?preview=true） */
    @GetMapping("/activity/{id}/preview")
    public String previewForm(@PathVariable Long id, Model model) {
        Map<String, Object> structure = activityService.getActivityStructure(id);
        model.addAllAttributes(structure);
        model.addAttribute("preview", true);
        model.addAttribute("activityId", id);
        return "student/form";
    }

    // ============ 学生名单管理 ============

    /** 查看活动学生名单（含提交状态） */
    @GetMapping("/activity/{id}/roster")
    public String viewRoster(@PathVariable Long id, HttpServletRequest request, Model model) {
        Activity activity = activityService.getActivityById(id)
                .orElseThrow(() -> new BusinessException(messageSource.getMessage("teacher.activity.error.notFound",
                        new Object[]{id}, request.getLocale())));
        List<StudentRosterService.RosterWithStatus> roster = rosterService.getRosterWithSubmissionStatus(id);
        long submittedCount = roster.stream().filter(StudentRosterService.RosterWithStatus::submitted).count();
        model.addAttribute("activity", activity);
        model.addAttribute("roster", roster);
        model.addAttribute("rosterCount", roster.size());
        model.addAttribute("submittedCount", submittedCount);
        model.addAttribute("unsubmittedCount", roster.size() - submittedCount);
        return "teacher/roster";
    }

    /** 批量导入学生名单（Excel） */
    @PostMapping("/activity/{id}/roster/import")
    public String importRoster(@PathVariable Long id,
                               @RequestParam("file") MultipartFile file,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        try {
            StudentRosterService.ImportResult result = rosterService.importFromExcel(id, file);
            auditLogService.record(getCurrentOperator(request), "teacher", "IMPORT_ROSTER",
                    "activity", String.valueOf(id),
                    "导入学生名单：" + result.imported() + " 人，跳过 " + result.skipped() + " 行",
                    getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.roster.msg.importResult",
                            new Object[]{result.imported(), result.skipped() > 0 ? "（跳过 " + result.skipped() + " 行无效数据）" : ""},
                            request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/activity/" + id + "/roster";
    }

    /** 下载名单模板（空 Excel 表头） */
    @GetMapping("/activity/{id}/roster/template")
    public void downloadRosterTemplate(@PathVariable Long id,
                                       HttpServletResponse response) throws IOException {
        String fileName = "学生名单模板.xlsx";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        rosterService.exportTemplate(response.getOutputStream());
        response.getOutputStream().flush();
    }

    /** 清空活动名单 */
    @PostMapping("/activity/{id}/roster/clear")
    public String clearRoster(@PathVariable Long id,
                              HttpServletRequest request,
                              RedirectAttributes redirectAttributes) {
        try {
            rosterService.clearRoster(id);
            auditLogService.record(getCurrentOperator(request), "teacher", "CLEAR_ROSTER",
                    "activity", String.valueOf(id),
                    "清空学生名单", getClientIp(request));
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.roster.msg.cleared", null, request.getLocale()));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("teacher.roster.msg.clearFailed", new Object[]{e.getMessage()}, request.getLocale()));
        }
        return "redirect:/teacher/activity/" + id + "/roster";
    }

    // ============ 私有辅助 ============
    /** 登录页 error 参数 → 友好提示 */
    private String mapError(String error, HttpServletRequest request) {
        return "timeout".equals(error) ? messageSource.getMessage("teacher.login.error.timeout", null, request.getLocale())
                : "logout".equals(error) ? messageSource.getMessage("teacher.login.error.logout", null, request.getLocale())
                : "forbidden".equals(error) ? messageSource.getMessage("teacher.login.error.forbidden", null, request.getLocale())
                : messageSource.getMessage("teacher.login.error.generic", null, request.getLocale());
    }

    /** 从 request 属性获取当前操作人工号（由 JwtInterceptor 注入） */
    private String getCurrentOperator(HttpServletRequest request) {
        return ControllerHelper.getCurrentOperator(request);
    }

    /** 获取客户端真实 IP（穿透代理） */
    private String getClientIp(HttpServletRequest request) {
        return ControllerHelper.getClientIp(request);
    }

    /* ===================== 教师账号管理 ===================== */

    /** 审计日志查看页面（分页，按时间倒序） */
    @GetMapping("/audit-logs")
    public String auditLogs(@RequestParam(defaultValue = "0") @Min(0) int page,
                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                            Model model) {
        org.springframework.data.domain.Page<com.skillbridge.model.AuditLog> logPage =
                auditLogService.getLogs(page, size);
        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("totalPages", logPage.getTotalPages());
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNext", logPage.hasNext());
        model.addAttribute("hasPrevious", logPage.hasPrevious());
        return "teacher/audit-logs";
    }

    /** 账号管理页面 */
    @GetMapping("/accounts")
    public String listAccounts(Model model) {
        model.addAttribute("teachers", teacherService.findAllTeachers());
        return "teacher/accounts";
    }

    /** 创建教师账号 */
    @PostMapping("/accounts/create")
    public String createAccount(@RequestParam("teacherId") @NotBlank @Pattern(regexp = "^[a-zA-Z0-9]{2,20}$", message = "工号格式不正确") String teacherId,
                                @RequestParam("name") @NotBlank @Size(max = 50, message = "姓名长度不能超过 50") String name,
                                @RequestParam("password") @Size(min = 6, max = 100, message = "密码长度至少 6 位") String password,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            teacherService.createTeacher(teacherId, password, name);
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.account.created", new Object[]{name}, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/accounts";
    }

    /** 切换账号状态（启用/禁用） */
    @PostMapping("/accounts/{id}/toggle-status")
    public String toggleAccountStatus(@PathVariable Long id,
                                      HttpServletRequest request,
                                      RedirectAttributes redirectAttributes) {
        try {
            Teacher teacher = teacherService.toggleStatus(id);
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage(
                            teacher.getStatus() == 0 ? "teacher.account.enabled" : "teacher.account.disabled",
                            new Object[]{teacher.getName()}, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/accounts";
    }

    /** 重置密码 */
    @PostMapping("/accounts/{id}/reset-password")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam("newPassword") @Size(min = 6, max = 100, message = "密码长度至少 6 位") String newPassword,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            teacherService.resetPassword(id, newPassword);
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("teacher.account.passwordReset", null, request.getLocale()));
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/teacher/accounts";
    }
}
