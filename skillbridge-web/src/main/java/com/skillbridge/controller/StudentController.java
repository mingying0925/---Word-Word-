package com.skillbridge.controller;

import com.skillbridge.model.Activity;
import com.skillbridge.model.StudentRosterEntry;
import com.skillbridge.model.Submission;
import com.skillbridge.service.ActivityService;
import com.skillbridge.service.BusinessException;
import com.skillbridge.service.StudentRosterService;
import com.skillbridge.utils.CookieHelper;
import com.skillbridge.utils.CryptoUtil;
import com.skillbridge.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/student")
public class StudentController {

    private final ActivityService activityService;
    private final JwtUtil jwtUtil;
    private final CookieHelper cookieHelper;
    private final CryptoUtil cryptoUtil;
    private final StudentRosterService rosterService;
    private final MessageSource messageSource;

    @Value("${app.upload-dir:${user.dir}/uploads}")
    private String uploadDir;

    public StudentController(ActivityService activityService,
                             JwtUtil jwtUtil,
                             CookieHelper cookieHelper,
                             CryptoUtil cryptoUtil,
                             StudentRosterService rosterService,
                             MessageSource messageSource) {
        this.activityService = activityService;
        this.jwtUtil = jwtUtil;
        this.cookieHelper = cookieHelper;
        this.cryptoUtil = cryptoUtil;
        this.rosterService = rosterService;
        this.messageSource = messageSource;
    }

    // ============ 双入口：学生登录 ============
    /** 学生专属登录页（学号 + 身份证），可选 ?activityId= 绑定活动 */
    @GetMapping("/login")
    public String showStudentLogin(@RequestParam(value = "activityId", required = false) Long activityId,
                                   @RequestParam(value = "error", required = false) String error,
                                   HttpServletRequest request,
                                   Model model) {
        if (activityId != null) {
            Activity activity = activityService.getActivityById(activityId)
                    .orElseThrow(() -> new BusinessException("活动不存在: " + activityId));
            model.addAttribute("activity", activity);
            model.addAttribute("activityId", activityId);
        }
        if (error != null) {
            model.addAttribute("error", mapError(error, request));
        }
        return "student/login";
    }

    /** 学生登录提交 → 生成 student 角色 JWT → 按 activityId 跳转填表 */
    @PostMapping("/login")
    public String handleStudentLogin(@RequestParam(value = "activityId", required = false) Long activityId,
                                     @RequestParam("studentName") String studentName,
                                     @RequestParam("studentId") String studentId,
                                     @RequestParam("idCard") String idCard,
                                     HttpSession session,
                                     HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     RedirectAttributes redirectAttributes) {
        try {
            // 姓名格式校验：1-20 个字符
            if (studentName == null || studentName.trim().isEmpty() || studentName.trim().length() > 20) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("student.login.error.nameEmpty", null, request.getLocale()));
                return redirectBack(activityId);
            }
            studentName = studentName.trim();
            // 学号格式校验：字母或数字，4-20 位
            if (studentId == null || !studentId.matches("^[a-zA-Z0-9]{4,20}$")) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("student.login.error.studentIdInvalid", null, request.getLocale()));
                return redirectBack(activityId);
            }
            // 身份证号格式 + 校验位验证
            if (!isValidIdCard(idCard)) {
                redirectAttributes.addFlashAttribute("error",
                        messageSource.getMessage("student.login.error.idCardInvalid", null, request.getLocale()));
                return redirectBack(activityId);
            }
            // 白名单校验：若活动已导入学生名单，则仅允许名单内学生登录
            if (activityId != null && rosterService.isRosterEnabled(activityId)) {
                Optional<StudentRosterEntry> rosterEntry =
                        rosterService.checkWhitelist(activityId, studentId, idCard);
                if (rosterEntry.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "您不在本活动的学生名单中，请联系教师。");
                    return redirectBack(activityId);
                }
                // 名单中有姓名时，以名单姓名为准（避免学生填错）
                StudentRosterEntry entry = rosterEntry.get();
                if (entry.getStudentName() != null && !entry.getStudentName().isBlank()) {
                    studentName = entry.getStudentName();
                }
            }
            // 绑定活动时校验是否已提交过
            if (activityId != null) {
                Optional<Submission> existed = activityService.checkLogin(activityId, studentId, idCard);
                if (existed.isPresent()) {
                    // 已提交 → 存入 session 并跳转到查看页面（而非拒绝）
                    session.setAttribute("studentName", studentName);
                    session.setAttribute("studentId", studentId);
                    session.setAttribute("idCard", cryptoUtil.encrypt(idCard));
                    String token = jwtUtil.generateToken(studentId, "student");
                    cookieHelper.writeTokenCookie(response, token);
                    redirectAttributes.addFlashAttribute("info",
                            messageSource.getMessage("student.login.error.alreadySubmitted", null, request.getLocale()));
                    return "redirect:/student/activity/" + activityId + "/view-submission";
                }
            }
            // 身份信息存入 session（idCard 加密存储，避免服务端内存明文泄露）
            session.setAttribute("studentName", studentName);
            session.setAttribute("studentId", studentId);
            session.setAttribute("idCard", cryptoUtil.encrypt(idCard));
            // 生成 JWT 写入 Cookie，避免后续请求被拦截器拦截
            String token = jwtUtil.generateToken(studentId, "student");
            cookieHelper.writeTokenCookie(response, token);
            if (activityId != null) {
                return "redirect:/student/activity/" + activityId + "/form";
            }
            return "redirect:/student/activities";
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return redirectBack(activityId);
        }
    }

    /** 根据是否绑定活动返回对应的重定向路径 */
    private String redirectBack(Long activityId) {
        return activityId != null
                ? "redirect:/student/login?activityId=" + activityId
                : "redirect:/student/login";
    }

    /** 学生活动列表页：展示所有报名中的活动 */
    @GetMapping("/activities")
    public String listActivities(Model model) {
        List<Activity> activities = activityService.getAllActivities();
        model.addAttribute("activities", activities);
        return "student/activities";
    }

    /**
     * 校验中国大陆 18 位身份证号格式与校验位。
     * <p>
     * 规则：前 17 位为数字，第 18 位为数字或 X；
     * 采用 GB 11643-1999 加权校验算法验证最后一位校验码。
     *
     * @param idCard 身份证号字符串
     * @return true 表示格式与校验位均正确
     */
    private boolean isValidIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) {
            return false;
        }
        // 前 17 位必须为数字，第 18 位为数字或 X
        if (!idCard.matches("^\\d{17}[\\dXx]$")) {
            return false;
        }
        // 加权因子与校验码表（GB 11643-1999）
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkCodes = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * weights[i];
        }
        char expected = checkCodes[sum % 11];
        char actual = Character.toUpperCase(idCard.charAt(17));
        return expected == actual;
    }

    /** 旧的活动级登录入口 → 重定向到统一学生登录页（保留 activityId 上下文） */
    @GetMapping("/activity/{id}")
    public String legacyActivityLogin(@PathVariable Long id) {
        return "redirect:/student/login?activityId=" + id;
    }

    /** 3. 填表页面（根据 activity 的 bookmarksJson 动态生成表格） */
    @GetMapping("/activity/{id}/form")
    public String showForm(@PathVariable Long id,
                           HttpSession session,
                           HttpServletRequest request,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        String studentId = (String) session.getAttribute("studentId");
        String encryptedIdCard = (String) session.getAttribute("idCard");
        String studentName = (String) session.getAttribute("studentName");
        if (studentId == null || encryptedIdCard == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.login.error.needAuth", null, request.getLocale()));
            return "redirect:/student/login?activityId=" + id;
        }
        String idCard = cryptoUtil.decrypt(encryptedIdCard);
        Map<String, Object> structure = activityService.getActivityStructure(id);
        model.addAllAttributes(structure);
        model.addAttribute("activityId", id);
        model.addAttribute("studentId", studentId);
        model.addAttribute("studentName", studentName);
        model.addAttribute("idCard", idCard);
        model.addAttribute("preview", false);
        // 回填上次提交失败时保留的草稿数据（避免长表单一次失败全部丢失）
        @SuppressWarnings("unchecked")
        Map<String, String> draft = (Map<String, String>) session.getAttribute("formDraft");
        if (draft != null && !draft.isEmpty()) {
            model.addAttribute("formDraft", draft);
            // 草稿读取后立即清除，避免刷新页面后仍残留旧数据
            session.removeAttribute("formDraft");
        }
        return "student/form";
    }

    /** 4. 提交接口（POST），身份信息从 session 读取，支持图片上传 */
    @PostMapping("/activity/{id}/submit")
    public String handleSubmit(@PathVariable Long id,
                               HttpSession session,
                               HttpServletRequest request,
                               @RequestParam Map<String, String> allParams,
                               @RequestParam Map<String, MultipartFile> allFiles,
                               RedirectAttributes redirectAttributes) {
        String studentId = (String) session.getAttribute("studentId");
        String encryptedIdCard = (String) session.getAttribute("idCard");
        String studentName = (String) session.getAttribute("studentName");
        if (studentId == null || encryptedIdCard == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.login.error.sessionExpired", null, request.getLocale()));
            return "redirect:/student/login?activityId=" + id;
        }
        String idCard = cryptoUtil.decrypt(encryptedIdCard);
        try {
            // 过滤掉非书签参数
            Map<String, String> formData = new HashMap<>(allParams);
            formData.remove("studentId");
            formData.remove("idCard");
            formData.remove("_csrf");
            // 将登录时输入的姓名注入 formData，确保导出时能提取到姓名用于文件命名
            if (studentName != null && !studentName.isBlank()) {
                formData.put("姓名", studentName);
            }
            // 将工作经历表单字段（workExp[i].*）映射为 Word 书签名（work_start_1 等）
            activityService.mapWorkExperienceBookmarks(formData);

            // 处理图片上传：将文件保存到磁盘，路径存入 formData
            for (Map.Entry<String, MultipartFile> entry : allFiles.entrySet()) {
                MultipartFile file = entry.getValue();
                if (file == null || file.isEmpty()) {
                    continue;
                }
                // 校验文件类型（仅允许图片）
                String contentType = file.getContentType();
                if (contentType == null || !contentType.startsWith("image/")) {
                    continue;
                }
                // 校验文件大小（≤2MB）
                long maxImageSize = 2 * 1024 * 1024;
                if (file.getSize() > maxImageSize) {
                    // 保存草稿，避免长表单内容丢失
                    saveDraft(session, formData);
                    redirectAttributes.addFlashAttribute("error",
                            messageSource.getMessage("student.form.msg.photoTooLarge", null, "证件照大小不能超过 2MB，请压缩后重新上传。", request.getLocale()));
                    return "redirect:/student/activity/" + id + "/form";
                }
                // 校验字段名，防止路径穿越攻击
                String fieldName = entry.getKey();
                if (!fieldName.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{1,50}$")) {
                    continue;
                }
                // 保存图片到 uploads/images/{activityId}_{studentId}/
                String imageDir = uploadDir + "/images/" + id + "_" + studentId;
                Path dirPath = Paths.get(imageDir).toAbsolutePath().normalize();
                Files.createDirectories(dirPath);
                String originalName = file.getOriginalFilename();
                String ext = originalName != null && originalName.contains(".")
                        ? originalName.substring(originalName.lastIndexOf("."))
                        : ".jpg";
                String imageName = fieldName + ext;
                Path imagePath = dirPath.resolve(imageName).normalize();
                // 二次校验：确保最终路径仍在目标目录内
                if (!imagePath.startsWith(dirPath)) {
                    continue;
                }
                try (var is = file.getInputStream()) {
                    Files.copy(is, imagePath, StandardCopyOption.REPLACE_EXISTING);
                }
                // 将图片绝对路径存入 formData，Python 导出时按路径读取
                formData.put(fieldName, imagePath.toAbsolutePath().toString());
            }

            activityService.submitRegistration(id, studentId, idCard, formData);
            // 提交成功后清除草稿，但保留身份信息以便学生从成功页直接查看提交内容
            // 学生点击"退出"时会清除完整 session（/logout 端点）
            session.removeAttribute("formDraft");
            redirectAttributes.addFlashAttribute("msg",
                    messageSource.getMessage("student.form.msg.submitSuccess", null, request.getLocale()));
            return "redirect:/student/activity/" + id + "/success";
        } catch (BusinessException e) {
            // 业务异常时保存草稿，避免长表单内容丢失
            saveDraft(session, collectDraft(allParams));
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/activity/" + id + "/form";
        } catch (IOException e) {
            saveDraft(session, collectDraft(allParams));
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.form.msg.imageUploadFailed", null, request.getLocale()));
            return "redirect:/student/activity/" + id + "/form";
        }
    }

    /** 收集表单参数为草稿（剔除敏感字段） */
    private Map<String, String> collectDraft(Map<String, String> allParams) {
        Map<String, String> draft = new HashMap<>(allParams);
        draft.remove("studentId");
        draft.remove("idCard");
        draft.remove("_csrf");
        return draft;
    }

    /** 保存草稿到 session */
    private void saveDraft(HttpSession session, Map<String, String> draft) {
        if (draft != null && !draft.isEmpty()) {
            session.setAttribute("formDraft", draft);
        }
    }

    /** 提交成功页 */
    @GetMapping("/activity/{id}/success")
    public String showSuccess(@PathVariable Long id, Model model) {
        model.addAttribute("activityId", id);
        return "student/submit-success";
    }

    /**
     * 查看已提交的报名信息（只读）。
     * 学生再次登录时，若已提交则跳转到此页面，展示填写过的字段值。
     */
    @GetMapping("/activity/{id}/view-submission")
    public String viewSubmission(@PathVariable Long id,
                                 HttpSession session,
                                 HttpServletRequest request,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        String studentId = (String) session.getAttribute("studentId");
        String encryptedIdCard = (String) session.getAttribute("idCard");
        if (studentId == null || encryptedIdCard == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.login.error.needAuth", null, request.getLocale()));
            return "redirect:/student/login?activityId=" + id;
        }
        String idCard = cryptoUtil.decrypt(encryptedIdCard);
        Optional<Submission> existed = activityService.checkLogin(id, studentId, idCard);
        if (existed.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.download.notFound", null, request.getLocale()));
            return "redirect:/student/login?activityId=" + id;
        }
        Submission submission = existed.get();
        // 解析 formDataJson 为有序键值对
        Map<String, String> formData = activityService.parseFormDataJson(submission.getFormDataJson());
        model.addAttribute("activity", submission.getActivity());
        model.addAttribute("submission", submission);
        model.addAttribute("formData", formData);
        model.addAttribute("activityId", id);
        return "student/view-submission";
    }

    /**
     * 学生下载自己提交的 Word 文件。
     * 仅允许下载本人提交记录对应的 Word，防止越权下载他人文件。
     */
    @GetMapping("/activity/{id}/download")
    public String downloadOwnWord(@PathVariable Long id,
                                  HttpSession session,
                                  HttpServletRequest request,
                                  HttpServletResponse response,
                                  RedirectAttributes redirectAttributes) throws IOException {
        String studentId = (String) session.getAttribute("studentId");
        String encryptedIdCard = (String) session.getAttribute("idCard");
        if (studentId == null || encryptedIdCard == null) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.login.error.needAuth", null, request.getLocale()));
            return "redirect:/student/login?activityId=" + id;
        }
        String idCard = cryptoUtil.decrypt(encryptedIdCard);
        Optional<Submission> existed = activityService.checkLogin(id, studentId, idCard);
        if (existed.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    messageSource.getMessage("student.download.notFound", null, request.getLocale()));
            return "redirect:/student/login?activityId=" + id;
        }
        Submission submission = existed.get();
        try {
            byte[] docx = activityService.exportWord(submission.getId());
            String studentName = (String) session.getAttribute("studentName");
            String fileName = (studentName != null ? studentName : "报名表") + "_" + studentId + ".docx";
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + java.net.URLEncoder.encode(fileName, "UTF-8") + "\"");
            response.getOutputStream().write(docx);
            response.getOutputStream().flush();
            return null;
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/student/activity/" + id + "/view-submission";
        }
    }

    // ============ 私有辅助 ============
    /** 登录页 error 参数 → 友好提示 */
    private String mapError(String error, HttpServletRequest request) {
        return "timeout".equals(error) ? messageSource.getMessage("student.login.error.timeout", null, request.getLocale())
                : "logout".equals(error) ? messageSource.getMessage("student.login.error.logout", null, request.getLocale())
                : "forbidden".equals(error) ? messageSource.getMessage("student.login.error.forbidden", null, request.getLocale())
                : messageSource.getMessage("student.login.error.generic", null, request.getLocale());
    }
}
