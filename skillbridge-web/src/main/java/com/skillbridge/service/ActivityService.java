package com.skillbridge.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.model.Activity;
import com.skillbridge.model.ActivityStatus;
import com.skillbridge.model.Submission;
import com.skillbridge.repository.ActivityRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityRepository activityRepository;
    private final SubmissionRepository submissionRepository;
    private final PythonExportClient pythonExportClient;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Value("${app.upload-dir:${user.dir}/uploads}")
    private String uploadDir;

    public ActivityService(ActivityRepository activityRepository,
                           SubmissionRepository submissionRepository,
                           PythonExportClient pythonExportClient,
                           FileStorageService fileStorageService,
                           ObjectMapper objectMapper) {
        this.activityRepository = activityRepository;
        this.submissionRepository = submissionRepository;
        this.pythonExportClient = pythonExportClient;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /* ===================== 活动相关 ===================== */

    /** 草稿状态值：活动已上传模板并解析，但教师尚未确认字段 */
    private static final int STATUS_DRAFT = ActivityStatus.DRAFT.getCode();

    /**
     * 校验当前教师是否为指定活动的归属人。
     * 若活动的 ownerId 为 null（历史数据），跳过校验以保持向后兼容。
     *
     * @param activity 目标活动
     * @param ownerId  当前教师工号
     * @throws BusinessException 当 ownerId 不匹配时
     */
    public void assertOwner(Activity activity, String ownerId) {
        if (activity == null) {
            throw new BusinessException("活动不存在");
        }
        // 历史数据（ownerId 为 null）暂不强制归属，保持向后兼容
        if (activity.getOwnerId() != null && !activity.getOwnerId().equals(ownerId)) {
            throw new BusinessException("无权操作他人创建的活动");
        }
    }

    /**
     * 按归属教师分页查询非草稿活动。
     */
    public Page<Activity> getActivitiesPageByOwner(String ownerId, int page, int size) {
        return activityRepository.findByOwnerIdAndStatusNot(ownerId, STATUS_DRAFT,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /**
     * 按归属教师搜索活动（名称 + 状态）。
     */
    public Page<Activity> searchActivitiesByOwner(String ownerId, String keyword, Integer status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword && status != null) {
            return activityRepository.findByOwnerIdAndNameContainingAndStatus(ownerId, keyword.trim(), status, pageable);
        }
        if (hasKeyword) {
            return activityRepository.findByOwnerIdAndNameContainingAndStatusNot(ownerId, keyword.trim(), STATUS_DRAFT, pageable);
        }
        if (status != null) {
            return activityRepository.findByOwnerIdAndStatus(ownerId, status, pageable);
        }
        return activityRepository.findByOwnerIdAndStatusNot(ownerId, STATUS_DRAFT, pageable);
    }

    public List<Activity> getAllActivities() {
        return activityRepository.findByStatusNot(STATUS_DRAFT);
    }

    public Page<Activity> getActivitiesPage(int page, int size) {
        return activityRepository.findByStatusNot(STATUS_DRAFT,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    /**
     * 按名称关键字与状态搜索活动（排除草稿）。
     * <p>
     * 搜索规则：
     * <ul>
     *   <li>keyword 与 status 均为空 → 查询所有非草稿活动</li>
     *   <li>仅 keyword 非空 → 按名称模糊匹配（排除草稿）</li>
     *   <li>仅 status 非空 → 按精确状态查询（可查草稿：status=2）</li>
     *   <li>两者均非空 → 名称 + 状态组合查询</li>
     * </ul>
     *
     * @param keyword 名称关键字（可空）
     * @param status  状态值：0=报名中, 1=已截止, 2=草稿, null=不限
     * @param page    页码（从 0 开始）
     * @param size    每页条数
     * @return 分页结果
     */
    public Page<Activity> searchActivities(String keyword, Integer status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        if (hasKeyword && status != null) {
            return activityRepository.findByNameContainingAndStatus(keyword.trim(), status, pageable);
        }
        if (hasKeyword) {
            return activityRepository.findByNameContainingAndStatusNot(keyword.trim(), STATUS_DRAFT, pageable);
        }
        if (status != null) {
            return activityRepository.findByStatus(status, pageable);
        }
        return activityRepository.findByStatusNot(STATUS_DRAFT, pageable);
    }

    public Optional<Activity> getActivityById(Long id) {
        return activityRepository.findById(id);
    }

    /**
     * 创建活动：保存上传文件到本地 uploads/ 目录 → 调用 Python 解析书签 → 保存活动。
     */
    public Activity createActivity(String name, LocalDateTime deadline, MultipartFile templateFile, String ownerId) {
        PendingTemplate pending = parseTemplate(templateFile);
        return saveActivity(name, deadline, pending.templatePath(), pending.bookmarksJson(), ownerId);
    }

    /**
     * 步骤 1：保存模板文件并解析书签，返回待确认数据（尚未持久化为活动）。
     * 用于教师上传后的字段预览/确认流程（PRD F1/F2）。
     */
    public PendingTemplate parseTemplate(MultipartFile templateFile) {
        if (templateFile == null || templateFile.isEmpty()) {
            throw new BusinessException("请上传 Word 模板文件");
        }
        String original = templateFile.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".docx")) {
            throw new BusinessException("仅支持 .docx 模板文件");
        }

        Path savedPath = saveTemplateFile(templateFile);

        String bookmarksJson;
        try {
            bookmarksJson = pythonExportClient.parseBookmarks(templateFile);
        } catch (BusinessException e) {
            // 解析失败时删除已保存的模板文件，避免脏数据
            tryDelete(savedPath);
            throw e;
        }
        return new PendingTemplate(savedPath.toAbsolutePath().toString(), bookmarksJson);
    }

    /**
     * 步骤 2：教师确认字段后，将活动持久化保存。
     */
    public Activity saveActivity(String name, LocalDateTime deadline, String templatePath, String bookmarksJson, String ownerId) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setOwnerId(ownerId);
        activity.setDeadline(deadline);
        activity.setTemplatePath(templatePath);
        activity.setBookmarksJson(bookmarksJson);
        activity.setStatus(ActivityStatus.ACTIVE.getCode());
        return activityRepository.save(activity);
    }

    /**
     * 步骤 1（草稿模式）：保存上传的模板与解析结果为草稿活动（status=2）。
     * <p>
     * 替代原 HttpSession 暂存方案，支持集群部署。
     * 教师确认字段后通过 {@link #confirmDraft(Long)} 转为正式活动。
     *
     * @param name          活动名称
     * @param deadline      截止时间（可空）
     * @param templatePath  模板文件绝对路径
     * @param bookmarksJson 书签解析 JSON
     * @param ownerId       创建教师工号（归属校验用）
     * @return 已保存的草稿活动（含 ID）
     */
    public Activity saveDraft(String name, LocalDateTime deadline, String templatePath, String bookmarksJson, String ownerId) {
        Activity activity = new Activity();
        activity.setName(name);
        activity.setOwnerId(ownerId);
        activity.setDeadline(deadline);
        activity.setTemplatePath(templatePath);
        activity.setBookmarksJson(bookmarksJson);
        activity.setStatus(ActivityStatus.DRAFT.getCode());
        return activityRepository.save(activity);
    }

    /**
     * 获取草稿活动。仅返回 status=2 的活动，非草稿或不存在时抛异常。
     * 同时校验归属，防止教师查看他人草稿。
     */
    public Activity getDraftById(Long id, String ownerId) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("草稿不存在: " + id));
        if (!ActivityStatus.DRAFT.matches(activity.getStatus())) {
            throw new BusinessException("该活动不是草稿状态，无法编辑: " + id);
        }
        assertOwner(activity, ownerId);
        return activity;
    }

    /**
     * 确认草稿：将 status 从 2（草稿）改为 0（报名中）。
     */
    @Transactional
    public Activity confirmDraft(Long id, String ownerId) {
        Activity activity = getDraftById(id, ownerId);
        activity.setStatus(ActivityStatus.ACTIVE.getCode());
        return activityRepository.save(activity);
    }

    /**
     * 删除草稿：删除草稿活动记录及其模板文件。
     */
    @Transactional
    public void deleteDraft(Long id, String ownerId) {
        Activity activity = getDraftById(id, ownerId);
        if (activity.getTemplatePath() != null) {
            tryDelete(Paths.get(activity.getTemplatePath()));
        }
        activityRepository.delete(activity);
    }

    /**
     * 更新草稿中书签字段的元数据（显示名、必填、启用）。
     * <p>
     * 教师在确认页编辑字段配置后，将配置合并到 bookmarksJson 的 bookmarks 数组中。
     * 每个书签对象新增字段：
     * <ul>
     *   <li>displayName：显示名（默认为书签原名称）</li>
     *   <li>required：是否必填（默认 true）</li>
     *   <li>enabled：是否启用（默认 true，false 时学生填表页不渲染该字段）</li>
     * </ul>
     *
     * @param draftId         草稿活动 ID
     * @param fieldConfigsJson 字段配置 JSON 数组，格式：[{"name":"书签名","displayName":"显示名","required":true,"enabled":true}]
     */
    @Transactional
    public Activity updateDraftFields(Long draftId, String fieldConfigsJson, String ownerId) {
        Activity draft = getDraftById(draftId, ownerId);
        if (fieldConfigsJson == null || fieldConfigsJson.isBlank()) {
            return draft;
        }
        try {
            // 解析教师提交的字段配置
            List<Map<String, Object>> fieldConfigs = objectMapper.readValue(fieldConfigsJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            // 解析现有 bookmarksJson
            JsonNode root = objectMapper.readTree(draft.getBookmarksJson());
            Map<String, Object> rootMap = objectMapper.convertValue(root,
                    new TypeReference<LinkedHashMap<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> bookmarks = (List<Map<String, Object>>) rootMap.get("bookmarks");
            if (bookmarks != null && !fieldConfigs.isEmpty()) {
                // 按书签名建立配置查找表
                Map<String, Map<String, Object>> configByName = new LinkedHashMap<>();
                for (Map<String, Object> fc : fieldConfigs) {
                    Object name = fc.get("name");
                    if (name instanceof String s) {
                        configByName.put(s, fc);
                    }
                }
                // 合并配置到 bookmarks
                for (Map<String, Object> bm : bookmarks) {
                    Object name = bm.get("name");
                    if (name instanceof String s) {
                        Map<String, Object> config = configByName.get(s);
                        if (config != null) {
                            Object displayName = config.get("displayName");
                            bm.put("displayName", displayName != null ? displayName : s);
                            Object required = config.get("required");
                            bm.put("required", required instanceof Boolean ? required : true);
                            Object enabled = config.get("enabled");
                            bm.put("enabled", enabled instanceof Boolean ? enabled : true);
                        }
                    }
                }
            }
            rootMap.put("bookmarks", bookmarks);
            draft.setBookmarksJson(objectMapper.writeValueAsString(rootMap));
            return activityRepository.save(draft);
        } catch (Exception e) {
            throw new BusinessException("更新字段配置失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解析原始 bookmarksJson 为前端渲染结构（用于确认页，无需已保存的活动）。
     */
    public Map<String, Object> parseStructureFromJson(String bookmarksJson) {
        Activity temp = new Activity();
        temp.setBookmarksJson(bookmarksJson);
        return parseStructure(temp);
    }

    /** 待确认的模板解析结果。 */
    public record PendingTemplate(String templatePath, String bookmarksJson) {}

    @Transactional
    public void deleteActivity(Long id, String ownerId) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在: " + id));
        assertOwner(activity, ownerId);
        // 级联删除关联的提交记录
        List<Submission> submissions = submissionRepository.findByActivityId(id);
        submissionRepository.deleteAll(submissions);
        // 删除模板文件（失败不影响删除流程）
        if (activity.getTemplatePath() != null) {
            tryDelete(Paths.get(activity.getTemplatePath()));
        }
        activityRepository.delete(activity);
    }

    /**
     * 截止活动报名：将状态从 0（报名中）改为 1（已截止）。
     * 截止后学生无法再提交，但教师仍可查看与导出。
     */
    public Activity closeActivity(Long id, String ownerId) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在: " + id));
        assertOwner(activity, ownerId);
        if (ActivityStatus.CLOSED.matches(activity.getStatus())) {
            throw new BusinessException("该活动已截止，无需重复操作");
        }
        activity.setStatus(ActivityStatus.CLOSED.getCode());
        return activityRepository.save(activity);
    }

    /**
     * 获取活动及解析后的表格结构（用于前端渲染）。
     * 返回 Map：{ activity, tablesStructure, bookmarks }
     */
    public Map<String, Object> getActivityStructure(Long activityId) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在: " + activityId));
        return parseStructure(activity);
    }

    /**
     * 解析 activity.bookmarksJson，返回 tablesStructure 与 bookmarks 列表。
     */
    public Map<String, Object> parseStructure(Activity activity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activity", activity);
        if (activity.getBookmarksJson() == null || activity.getBookmarksJson().isBlank()) {
            result.put("tablesStructure", Collections.emptyList());
            result.put("bookmarks", Collections.emptyList());
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(activity.getBookmarksJson());
            JsonNode tablesNode = root.path("tables_structure");
            JsonNode bookmarksNode = root.path("bookmarks");

            List<Map<String, Object>> tables = objectMapper.convertValue(
                    tablesNode, new TypeReference<List<Map<String, Object>>>() {});
            List<Map<String, Object>> bookmarks = objectMapper.convertValue(
                    bookmarksNode, new TypeReference<List<Map<String, Object>>>() {});

            // 为前端渲染预先计算每个单元格的 rowspan / colspan
            // 同时传入 bookmarks 列表，用于查找每个书签的元数据（type/options/displayName/required/enabled）
            List<Map<String, Object>> renderedTables = buildRenderTables(tables, bookmarks);

            result.put("tablesStructure", renderedTables);
            result.put("bookmarks", bookmarks);
            // 检测模板是否包含工作经历书签（work_start_N, work_company_N 等）
            result.put("hasWorkExperience", hasWorkExperienceBookmarks(bookmarks));
        } catch (Exception e) {
            throw new BusinessException("解析书签 JSON 失败：" + e.getMessage(), e);
        }
        return result;
    }

    /**
     * 检测书签列表中是否包含工作经历书签（名称以 work_ 开头）。
     * 用于前端条件渲染工作经历动态表格。
     */
    private boolean hasWorkExperienceBookmarks(List<Map<String, Object>> bookmarks) {
        if (bookmarks == null || bookmarks.isEmpty()) return false;
        return bookmarks.stream()
                .anyMatch(bm -> {
                    Object name = bm.get("name");
                    return name instanceof String s && s.startsWith("work_");
                });
    }

    /**
     * 将前端工作经历表单数据（workExp[i].startDate 等）映射为 Word 书签名（work_start_1 等）。
     * <p>
     * 映射关系：
     * <ul>
     *   <li>workExp[i].startDate + workExp[i].endDate → work_start_{i+1}（合并为 "起 - 止"）</li>
     *   <li>workExp[i].companyName → work_company_{i+1}</li>
     *   <li>workExp[i].position → work_position_{i+1}</li>
     * </ul>
     *
     * @param formData 原始表单数据（会被原地修改：移除 workExp.* 键，添加 work_*_N 键）
     */
    public void mapWorkExperienceBookmarks(Map<String, String> formData) {
        // 收集所有 workExp 索引
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^workExp\\[(\\d+)]\\.(\\w+)$");
        java.util.Map<Integer, java.util.Map<String, String>> workData = new java.util.TreeMap<>();
        for (var entry : new java.util.HashMap<>(formData).entrySet()) {
            var matcher = pattern.matcher(entry.getKey());
            if (matcher.matches()) {
                int idx = Integer.parseInt(matcher.group(1));
                String field = matcher.group(2);
                workData.computeIfAbsent(idx, k -> new java.util.HashMap<>())
                        .put(field, entry.getValue());
                formData.remove(entry.getKey());
            }
        }
        // 映射为书签名
        for (var entry : workData.entrySet()) {
            int row = entry.getKey() + 1; // 书签从 1 开始
            java.util.Map<String, String> fields = entry.getValue();
            String startDate = fields.get("startDate");
            String endDate = fields.get("endDate");
            if (startDate != null || endDate != null) {
                String dateRange = "";
                if (startDate != null && endDate != null) {
                    dateRange = startDate + " - " + endDate;
                } else if (startDate != null) {
                    dateRange = startDate;
                } else {
                    dateRange = endDate;
                }
                formData.put("work_start_" + row, dateRange);
            }
            String company = fields.get("companyName");
            if (company != null && !company.isBlank()) {
                formData.put("work_company_" + row, company);
            }
            String position = fields.get("position");
            if (position != null && !position.isBlank()) {
                formData.put("work_position_" + row, position);
            }
        }
    }

    /**
     * 将 Python 返回的 cells 列表（含 is_merged/merge_span）转换为按行分组的渲染结构，
     * 并预先计算每个单元格的 rowspan/colspan，便于 Thymeleaf 直接渲染。
     *
     * 输出：List<table>，每个 table = { tableIndex, rows: List<row> }
     * 每个 row = List<cell>，cell = { text, bookmarkNames, type, options, rowspan, colspan, isBookmark,
     *   displayName, required, enabled, bookmarkDisplayNames, bookmarkRequired, bookmarkEnabled }
     *
     * 书签元数据（type/options/displayName/required/enabled）从 bookmarks 列表中查找；
     * 若 bookmarks 中未定义，则回退到基于书签名称的推断逻辑（向后兼容）。
     *
     * 合并策略：
     *  - merge_span 为整数 > 1 → colspan = merge_span
     *  - merge_span == "continue" → 跳过（不渲染，已被 rowspan 占位）
     *  - merge_span == "restart" → rowspan 由后续 continue 数量 + 1 决定
     */
    private List<Map<String, Object>> buildRenderTables(List<Map<String, Object>> tables,
                                                         List<Map<String, Object>> bookmarks) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (tables == null) return result;

        // 构建书签元数据查找表：name → bookmark map
        Map<String, Map<String, Object>> bmLookup = new LinkedHashMap<>();
        if (bookmarks != null) {
            for (Map<String, Object> bm : bookmarks) {
                Object name = bm.get("name");
                if (name instanceof String s) {
                    bmLookup.put(s, bm);
                }
            }
        }

        for (Map<String, Object> table : tables) {
            Map<String, Object> outTable = new LinkedHashMap<>();
            Object tIdx = table.get("table_index");
            outTable.put("tableIndex", tIdx);

            List<Map<String, Object>> cells = (List<Map<String, Object>>) table.get("cells");
            if (cells == null) cells = Collections.emptyList();

            // 按 row 分组
            Map<Integer, List<Map<String, Object>>> byRow = new LinkedHashMap<>();
            int maxRow = 0;
            for (Map<String, Object> c : cells) {
                int r = toInt(c.get("row"));
                byRow.computeIfAbsent(r, k -> new ArrayList<>()).add(c);
                if (r > maxRow) maxRow = r;
            }

            // 先扫描所有 vMerge=restart 的 (row, col)，计算 rowspan
            // 通过 continue 数量统计
            // 构建 (row, col) -> cell 映射
            Map<String, Map<String, Object>> cellMap = new LinkedHashMap<>();
            for (Map<String, Object> c : cells) {
                int r = toInt(c.get("row"));
                int col = toInt(c.get("col"));
                cellMap.put(r + "," + col, c);
            }

            // 计算 vMerge=restart 的 rowspan
            Map<String, Integer> rowspanMap = new LinkedHashMap<>();
            for (Map<String, Object> c : cells) {
                Object span = c.get("merge_span");
                if ("restart".equals(span)) {
                    int r = toInt(c.get("row"));
                    int col = toInt(c.get("col"));
                    int spanCount = 1;
                    int nr = r + 1;
                    while (true) {
                        Map<String, Object> nc = cellMap.get(nr + "," + col);
                        if (nc != null && "continue".equals(nc.get("merge_span"))) {
                            spanCount++;
                            nr++;
                        } else {
                            break;
                        }
                    }
                    rowspanMap.put(r + "," + col, spanCount);
                }
            }

            // 按行构建渲染结构
            List<List<Map<String, Object>>> rows = new ArrayList<>();
            for (int r = 0; r <= maxRow; r++) {
                List<Map<String, Object>> rowCells = byRow.get(r);
                if (rowCells == null) continue;
                List<Map<String, Object>> outRow = new ArrayList<>();
                for (Map<String, Object> c : rowCells) {
                    Object span = c.get("merge_span");
                    // vMerge continue 单元格不渲染（已被 rowspan 占位）
                    if ("continue".equals(span)) {
                        continue;
                    }
                    Map<String, Object> outCell = new LinkedHashMap<>();
                    outCell.put("text", c.getOrDefault("text", ""));
                    List<String> bmNames = (List<String>) c.getOrDefault("bookmark_names", Collections.emptyList());
                    outCell.put("bookmarkNames", bmNames);
                    outCell.put("isBookmark", bmNames != null && !bmNames.isEmpty());

                    // 从 bookmarks 列表中查找每个书签的元数据（type/options/displayName/required/enabled）
                    // 若未找到，回退到基于名称的推断逻辑（向后兼容旧数据）
                    String cellType = "text";
                    List<String> cellOptions = null;
                    List<String> bookmarkTypes = new ArrayList<>();
                    List<String> bookmarkDisplayNames = new ArrayList<>();
                    List<Boolean> bookmarkRequired = new ArrayList<>();
                    List<Boolean> bookmarkEnabled = new ArrayList<>();
                    if (bmNames != null && !bmNames.isEmpty()) {
                        for (String bmName : bmNames) {
                            Map<String, Object> bmMeta = bmLookup.get(bmName);
                            String t = "text";
                            List<String> opts = null;
                            if (bmMeta != null) {
                                // 优先使用 bookmarks 列表中的 type/options
                                Object typeVal = bmMeta.get("type");
                                if (typeVal instanceof String ts && !ts.isBlank()) {
                                    t = ts;
                                }
                                Object optsVal = bmMeta.get("options");
                                if (optsVal instanceof List<?> list) {
                                    opts = list.stream()
                                            .filter(o -> o != null)
                                            .map(Object::toString)
                                            .toList();
                                }
                            }
                            // 回退：若 bookmarks 中未定义 type，则按名称推断
                            if (bmMeta == null || t.equals("text")) {
                                if (bmName.contains("性别")) {
                                    t = "radio";
                                    opts = List.of("男", "女");
                                } else if (bmName.contains("日期") || bmName.contains("时间")) {
                                    t = "date";
                                } else if (bmName.contains("照片") || bmName.toLowerCase().contains("photo")) {
                                    t = "image";
                                }
                            }
                            // 聚合到单元格级别
                            if ("radio".equals(t)) {
                                cellType = "radio";
                                cellOptions = opts;
                            } else if ("image".equals(t)) {
                                cellType = "image";
                            } else if ("date".equals(t) && "text".equals(cellType)) {
                                cellType = "date";
                            }
                            bookmarkTypes.add(t);
                            // displayName：优先从元数据获取，回退到书签原名
                            String dn = bmName;
                            if (bmMeta != null) {
                                Object dnVal = bmMeta.get("displayName");
                                if (dnVal instanceof String dns && !dns.isBlank()) {
                                    dn = dns;
                                }
                            }
                            bookmarkDisplayNames.add(dn);
                            // required：默认 true
                            boolean req = true;
                            if (bmMeta != null) {
                                Object reqVal = bmMeta.get("required");
                                if (reqVal instanceof Boolean b) {
                                    req = b;
                                }
                            }
                            bookmarkRequired.add(req);
                            // enabled：默认 true
                            boolean en = true;
                            if (bmMeta != null) {
                                Object enVal = bmMeta.get("enabled");
                                if (enVal instanceof Boolean b) {
                                    en = b;
                                }
                            }
                            bookmarkEnabled.add(en);
                        }
                    }
                    outCell.put("type", cellType);
                    outCell.put("options", cellOptions);
                    outCell.put("bookmarkTypes", bookmarkTypes);
                    outCell.put("bookmarkDisplayNames", bookmarkDisplayNames);
                    outCell.put("bookmarkRequired", bookmarkRequired);
                    outCell.put("bookmarkEnabled", bookmarkEnabled);
                    // 单元格级别的 required/enabled：所有书签都必填时才必填，任一启用即启用
                    boolean cellRequired = !bookmarkRequired.isEmpty() && bookmarkRequired.stream().allMatch(b -> b);
                    boolean cellEnabled = bookmarkEnabled.isEmpty() || bookmarkEnabled.stream().anyMatch(b -> b);
                    outCell.put("required", cellRequired);
                    outCell.put("enabled", cellEnabled);

                    // 计算 colspan / rowspan
                    int colspan = 1;
                    if (span instanceof Integer) {
                        colspan = (Integer) span;
                    } else if (span instanceof Number) {
                        colspan = ((Number) span).intValue();
                    }
                    outCell.put("colspan", colspan);

                    int rowIdx = toInt(c.get("row"));
                    int colIdx = toInt(c.get("col"));
                    int rowspan = rowspanMap.getOrDefault(rowIdx + "," + colIdx, 1);
                    outCell.put("rowspan", rowspan);

                    outRow.add(outCell);
                }
                rows.add(outRow);
            }
            outTable.put("rows", rows);
            result.add(outTable);
        }
        return result;
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /* ===================== 报名相关 ===================== */

    /**
     * 学生提交报名。校验同一活动下学号+身份证唯一。
     */
    @Transactional
    public Submission submitRegistration(Long activityId, String studentId, String idCard, Map<String, String> formData) {
        if (studentId == null || studentId.isBlank()) {
            throw new BusinessException("学号不能为空");
        }
        if (idCard == null || idCard.isBlank()) {
            throw new BusinessException("身份证号不能为空");
        }
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在: " + activityId));

        if (ActivityStatus.CLOSED.matches(activity.getStatus())) {
            throw new BusinessException("该活动已截止报名");
        }
        if (activity.getDeadline() != null && LocalDateTime.now().isAfter(activity.getDeadline())) {
            throw new BusinessException("该活动已过截止时间");
        }

        Optional<Submission> existed = submissionRepository
                .findByActivityIdAndStudentIdAndIdCard(activityId, studentId, idCard);
        if (existed.isPresent()) {
            throw new BusinessException("该学号+身份证已提交过报名，请勿重复提交");
        }

        Submission sub = new Submission();
        sub.setActivity(activity);
        sub.setStudentId(studentId);
        sub.setIdCard(idCard);
        try {
            sub.setFormDataJson(objectMapper.writeValueAsString(formData));
        } catch (Exception e) {
            throw new BusinessException("表单数据序列化失败：" + e.getMessage(), e);
        }
        return submissionRepository.save(sub);
    }

    /**
     * 校验学号+身份证是否已提交。返回 Optional<Submission>。
     */
    public Optional<Submission> checkLogin(Long activityId, String studentId, String idCard) {
        return submissionRepository.findByActivityIdAndStudentIdAndIdCard(activityId, studentId, idCard);
    }

    /**
     * 将 submission 的 formDataJson 解析为有序键值对（LinkedHashMap）。
     * 用于学生端查看已提交的报名信息。
     */
    public Map<String, String> parseFormDataJson(String formDataJson) {
        if (formDataJson == null || formDataJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(formDataJson,
                    new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            throw new BusinessException("表单数据解析失败：" + e.getMessage(), e);
        }
    }

    public List<Submission> getSubmissionsByActivityId(Long activityId) {
        return submissionRepository.findByActivityId(activityId);
    }

    /** 统计某活动的提交人数 */
    public long countSubmissionsByActivityId(Long activityId) {
        return submissionRepository.countByActivityId(activityId);
    }

    /** 批量统计多个活动的提交人数 */
    public Map<Long, Long> countSubmissionsByActivityIds(List<Long> activityIds) {
        List<Object[]> results = submissionRepository.countByActivityIds(activityIds);
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : results) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    public Submission getSubmission(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new BusinessException("报名记录不存在: " + submissionId));
    }

    /**
     * 导出 Word：根据 submissionId 取记录 → 解析 formDataJson → 调用 Python 填充 → 返回字节。
     */
    public byte[] exportWord(Long submissionId) {
        return exportWord(getSubmission(submissionId));
    }

    /** 导出 Word（直接传入 Submission，避免冗余查询） */
    public byte[] exportWord(Submission sub) {
        Activity activity = sub.getActivity();
        if (activity == null) {
            throw new BusinessException("报名记录未关联活动");
        }
        if (activity.getTemplatePath() == null) {
            throw new BusinessException("活动未配置模板文件");
        }

        Map<String, String> data = new LinkedHashMap<>();
        if (sub.getFormDataJson() != null && !sub.getFormDataJson().isBlank()) {
            try {
                Map<String, String> parsed = objectMapper.readValue(
                        sub.getFormDataJson(), new TypeReference<Map<String, String>>() {});
                data.putAll(parsed);
            } catch (Exception e) {
                throw new BusinessException("表单数据解析失败：" + e.getMessage(), e);
            }
        }
        return pythonExportClient.fillWord(activity.getTemplatePath(), data);
    }

    /**
     * 批量导出某活动下所有学生申报表为 ZIP 包，直接写入给定输出流。
     *
     * 业务流程：
     *   ① 校验活动存在、已配置模板、且有提交记录（校验失败抛 BusinessException，此时尚未写入任何字节）。
     *   ② 遍历每条 Submission，复用 exportWord 调用 Python 微服务生成 Word 字节流。
     *   ③ 将每个 Word 以「姓名.docx」为名写入 ZipOutputStream；姓名缺失时用「未命名申报表.docx」。
     *   ④ 单个学生导出失败时不中断整体流程，改为写入一个「姓名_导出失败.txt」占位说明。
     *
     * @param activityId 活动 ID
     * @param out        ZIP 字节流输出目标（通常是 HttpServletResponse.getOutputStream()）
     */
    public void exportAllAsZip(Long activityId, OutputStream out) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在: " + activityId));
        if (activity.getTemplatePath() == null) {
            throw new BusinessException("活动未配置模板文件，无法导出");
        }
        List<Submission> submissions = submissionRepository.findByActivityIdWithActivity(activityId);
        if (submissions.isEmpty()) {
            throw new BusinessException("该活动下暂无学生提交记录，无法导出");
        }

        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Submission sub : submissions) {
                String entryName = buildEntryName(sub);
                // 保证 ZIP 内文件名唯一，避免 ZipException
                String uniqueName = entryName;
                int counter = 1;
                while (usedNames.contains(uniqueName)) {
                    uniqueName = entryName.replace(".docx", "(" + counter + ").docx");
                    counter++;
                }
                usedNames.add(uniqueName);

                try {
                    byte[] docx = exportWord(sub);
                    zos.putNextEntry(new ZipEntry(uniqueName));
                    zos.write(docx);
                    zos.closeEntry();
                } catch (Exception e) {
                    // 单个学生失败不中断整体打包，写入错误说明占位文件
                    try {
                        String errName = uniqueName.replace(".docx", "_导出失败.txt");
                        zos.putNextEntry(new ZipEntry(errName));
                        zos.write(("该学生申报表导出失败：" + e.getMessage())
                                .getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    } catch (IOException e2) {
                        log.warn("导出失败占位文件写入失败: submissionId={}", sub.getId());
                    }
                }
            }
            zos.finish();
        } catch (IOException e) {
            throw new BusinessException("ZIP 打包失败：" + e.getMessage(), e);
        }
    }

    /**
     * 构造 ZIP 内单个学生的文件名：姓名.docx。
     * 姓名从 formDataJson 中动态查找；找不到则用「未命名申报表.docx」。
     */
    private String buildEntryName(Submission sub) {
        String name = extractStudentName(sub);
        if (name == null || name.isBlank()) {
            return "未命名申报表.docx";
        }
        return sanitizeFileName(name) + ".docx";
    }

    /**
     * 导出某活动下所有学生提交数据为 Excel（.xlsx）。
     * <p>
     * 列结构：
     * <ul>
     *   <li>固定列：序号、学号、身份证号（脱敏）、提交时间</li>
     *   <li>动态列：合并所有学生 formDataJson 的键（按首次出现顺序）</li>
     * </ul>
     *
     * @param activityId 活动 ID
     * @param out        Excel 字节流输出目标
     */
    public void exportExcel(Long activityId, OutputStream out) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在: " + activityId));
        List<Submission> submissions = submissionRepository.findByActivityId(activityId);
        if (submissions.isEmpty()) {
            throw new BusinessException("该活动下暂无学生提交记录，无法导出");
        }

        // 解析所有提交的 formDataJson，按首次出现顺序收集键
        List<Map<String, String>> parsedRows = new ArrayList<>();
        LinkedHashSet<String> dynamicColumns = new LinkedHashSet<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (Submission sub : submissions) {
            Map<String, String> row = new LinkedHashMap<>();
            if (sub.getFormDataJson() != null && !sub.getFormDataJson().isBlank()) {
                try {
                    Map<String, String> data = objectMapper.readValue(
                            sub.getFormDataJson(), new TypeReference<Map<String, String>>() {});
                    row.putAll(data);
                    dynamicColumns.addAll(data.keySet());
                } catch (Exception ignored) {
                    log.warn("Excel 导出时解析表单数据失败: submissionId={}", sub.getId());
                }
            }
            parsedRows.add(row);
        }

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sanitizeSheetName(activity.getName()));

            // 标题行样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(org.apache.poi.ss.usermodel.IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(org.apache.poi.ss.usermodel.FillPatternType.SOLID_FOREGROUND);

            // 构建表头：固定列 + 动态列
            List<String> headers = new ArrayList<>();
            headers.add("序号");
            headers.add("学号");
            headers.add("身份证号");
            headers.add("提交时间");
            headers.addAll(dynamicColumns);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 数据行
            for (int idx = 0; idx < parsedRows.size(); idx++) {
                Submission sub = submissions.get(idx);
                Map<String, String> data = parsedRows.get(idx);
                Row row = sheet.createRow(idx + 1);

                int col = 0;
                row.createCell(col++).setCellValue(idx + 1);
                row.createCell(col++).setCellValue(safeStr(sub.getStudentId()));
                row.createCell(col++).setCellValue(maskIdCard(sub.getIdCard()));
                row.createCell(col++).setCellValue(sub.getSubmitTime() != null
                        ? sub.getSubmitTime().format(fmt) : "");
                for (String key : dynamicColumns) {
                    row.createCell(col++).setCellValue(safeStr(data.get(key)));
                }
            }

            // 自动列宽（粗略）
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                // 限制最大宽度，避免过长
                int width = Math.min(sheet.getColumnWidth(i), 12000);
                sheet.setColumnWidth(i, width);
            }

            workbook.write(out);
            out.flush();
        } catch (IOException e) {
            throw new BusinessException("Excel 导出失败：" + e.getMessage(), e);
        }
    }

    /** 安全取字符串，null 返回空串 */
    private String safeStr(String s) {
        return s == null ? "" : s;
    }

    /** 身份证号脱敏：保留前 4 后 4，中间用 * 代替 */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard == null ? "" : idCard;
        }
        int len = idCard.length();
        return idCard.substring(0, 4) + "*".repeat(len - 8) + idCard.substring(len - 4);
    }

    /** 清理 Excel 工作表名非法字符（最多 31 字符） */
    private String sanitizeSheetName(String name) {
        if (name == null || name.isBlank()) return "活动数据";
        String cleaned = name.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    /**
     * 构造单个导出 Word 的文件名：姓名.docx。
     * 供 Controller 层设置 Content-Disposition 使用。
     * 姓名从 formDataJson 中动态查找；找不到则用「未命名申报表.docx」。
     */
    public String buildExportFileName(Long submissionId) {
        Submission sub = getSubmission(submissionId);
        String name = extractStudentName(sub);
        if (name == null || name.isBlank()) {
            return "未命名申报表.docx";
        }
        return sanitizeFileName(name) + ".docx";
    }

    /**
     * 从学生提交的表单数据中提取姓名。
     * 兼容查找键名包含「姓名」「name」「xm」的字段（忽略大小写），按优先级依次匹配。
     * <p>
     * 匹配策略：先精确匹配（避免 "username"/"nickname" 误命中 "name"），
     * 再对中文「姓名」做包含匹配（Word 书签可能含「学生姓名」等前缀）。
     * @return 提取到的姓名（已 trim）；找不到返回 null
     */
    private String extractStudentName(Submission sub) {
        if (sub.getFormDataJson() == null || sub.getFormDataJson().isBlank()) {
            return null;
        }
        try {
            Map<String, String> data = objectMapper.readValue(
                    sub.getFormDataJson(), new TypeReference<Map<String, String>>() {});
            // 已知姓名键名（按优先级），小写用于精确比较
            String[] exactKeys = {"姓名", "name", "xm"};
            // 第一轮：精确匹配（忽略大小写），避免误命中 username/nickname
            for (String key : exactKeys) {
                for (Map.Entry<String, String> entry : data.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                        continue;
                    }
                    if (entry.getKey().equalsIgnoreCase(key)) {
                        return entry.getValue().trim();
                    }
                }
            }
            // 第二轮：仅对中文「姓名」做包含匹配（Word 书签可能是「学生姓名」「姓名_1」等）
            for (Map.Entry<String, String> entry : data.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                if (entry.getKey().contains("姓名")) {
                    return entry.getValue().trim();
                }
            }
        } catch (Exception ignored) {
            log.warn("活动名称解析失败，回落至默认名");
        }
        return null;
    }

    /** 去除文件名中非法字符，保证 ZIP entry 名安全。 */
    private String sanitizeFileName(String name) {
        if (name == null) return "";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    /* ===================== 工具方法 ===================== */

    private Path saveTemplateFile(MultipartFile file) {
        String path = fileStorageService.store(file);
        return fileStorageService.resolveLocalPath(path);
    }

    private void tryDelete(Path path) {
        fileStorageService.delete(path != null ? path.toString() : null);
    }
}
