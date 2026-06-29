package com.skillbridge.service;

import com.skillbridge.model.StudentRosterEntry;
import com.skillbridge.repository.StudentRosterRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 学生名单服务。
 * <p>
 * 支持教师通过 Excel 批量导入学生名单，并在学生登录时进行白名单校验。
 * Excel 格式：学号 | 姓名 | 身份证号 | 班级（可选）
 */
@Service
public class StudentRosterService {

    private final StudentRosterRepository rosterRepository;
    private final SubmissionRepository submissionRepository;

    public StudentRosterService(StudentRosterRepository rosterRepository,
                                SubmissionRepository submissionRepository) {
        this.rosterRepository = rosterRepository;
        this.submissionRepository = submissionRepository;
    }

    /** 查询活动的所有名单条目（按学号排序） */
    public List<StudentRosterEntry> findByActivityId(Long activityId) {
        return rosterRepository.findByActivityIdOrderByStudentIdAsc(activityId);
    }

    /** 统计活动名单人数 */
    public long countByActivityId(Long activityId) {
        return rosterRepository.countByActivityId(activityId);
    }

    public Map<Long, Long> countByActivityIds(List<Long> activityIds) {
        List<Object[]> results = rosterRepository.countByActivityIds(activityIds);
        Map<Long, Long> counts = new java.util.HashMap<>();
        for (Object[] row : results) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * 白名单校验：检查学生是否在活动名单中。
     *
     * @return 若名单为空返回 empty（表示不启用白名单）；否则返回匹配的条目（empty 表示不在名单中）
     */
    public Optional<StudentRosterEntry> checkWhitelist(Long activityId, String studentId, String idCard) {
        return rosterRepository.findByActivityIdAndStudentIdAndIdCard(activityId, studentId, idCard);
    }

    /** 判断活动是否启用了名单（名单人数 > 0） */
    public boolean isRosterEnabled(Long activityId) {
        return countByActivityId(activityId) > 0;
    }

    /**
     * 从 Excel 批量导入学生名单。
     * <p>
     * 解析 Excel 文件，按行读取学生信息并保存。重复导入会先清空旧名单。
     * Excel 格式：第一行为表头（跳过），后续每行：学号 | 姓名 | 身份证号 | 班级（可选）
     *
     * @param activityId 活动 ID
     * @param file       Excel 文件（.xlsx）
     * @return 导入结果摘要
     */
    @Transactional
    public ImportResult importFromExcel(Long activityId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请上传 Excel 文件");
        }
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }

        List<StudentRosterEntry> entries = new ArrayList<>();
        Set<String> seenStudentIds = new HashSet<>();
        int skipped = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BusinessException("Excel 文件中没有工作表");
            }
            int lastRow = sheet.getLastRowNum();
            if (lastRow < 1) {
                throw new BusinessException("Excel 文件没有数据行（仅表头或为空）");
            }
            // 从第 2 行开始读取（第 1 行为表头）
            for (int i = 1; i <= lastRow; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String studentId = getCellAsString(row.getCell(0));
                String studentName = getCellAsString(row.getCell(1));
                String idCard = getCellAsString(row.getCell(2));
                String className = getCellAsString(row.getCell(3));

                // 跳过空行（学号或姓名或身份证号为空）
                if (isBlank(studentId) || isBlank(studentName) || isBlank(idCard)) {
                    skipped++;
                    continue;
                }
                studentId = studentId.trim();
                studentName = studentName.trim();
                idCard = idCard.trim();
                className = isBlank(className) ? null : className.trim();

                // 跳过同一文件内学号重复的行
                if (!seenStudentIds.add(studentId)) {
                    skipped++;
                    continue;
                }

                StudentRosterEntry entry = new StudentRosterEntry();
                entry.setActivityId(activityId);
                entry.setStudentId(studentId);
                entry.setStudentName(studentName);
                entry.setIdCard(idCard);
                entry.setClassName(className);
                entries.add(entry);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("解析 Excel 文件失败：" + e.getMessage(), e);
        } catch (Exception e) {
            throw new BusinessException("Excel 文件格式不正确：" + e.getMessage(), e);
        }

        if (entries.isEmpty()) {
            throw new BusinessException("Excel 文件中没有有效数据行（请检查表头是否占用了第一行，数据是否完整）");
        }

        // 重复导入时先清空旧名单
        rosterRepository.deleteByActivityId(activityId);
        rosterRepository.flush();
        // 批量保存
        rosterRepository.saveAll(entries);

        return new ImportResult(entries.size(), skipped);
    }

    /** 清空活动名单 */
    @Transactional
    public void clearRoster(Long activityId) {
        rosterRepository.deleteByActivityId(activityId);
    }

    /**
     * 获取名单及提交状态（用于教师查看谁已提交/未提交）。
     *
     * @return 每条名单附带 submitted 标志
     */
    public List<RosterWithStatus> getRosterWithSubmissionStatus(Long activityId) {
        List<StudentRosterEntry> entries = findByActivityId(activityId);
        if (entries.isEmpty()) {
            return List.of();
        }
        // 查询已提交的学号集合
        Set<String> submittedStudentIds = new HashSet<>();
        submissionRepository.findByActivityId(activityId).forEach(s -> {
            if (s.getStudentId() != null) {
                submittedStudentIds.add(s.getStudentId());
            }
        });
        List<RosterWithStatus> result = new ArrayList<>(entries.size());
        for (StudentRosterEntry e : entries) {
            result.add(new RosterWithStatus(e, submittedStudentIds.contains(e.getStudentId())));
        }
        return result;
    }

    /**
     * 导出名单模板（空表头）到输出流，供教师下载填写。
     */
    public void exportTemplate(OutputStream out) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("学生名单");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("身份证号");
            header.createCell(3).setCellValue("班级（可选）");
            // 设置列宽
            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 3000);
            sheet.setColumnWidth(2, 6000);
            sheet.setColumnWidth(3, 4000);
            workbook.write(out);
        }
    }

    // ============ 私有辅助 ============

    private String getCellAsString(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                // 避免学号/身份证号被解析为科学计数法
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) {
                    yield String.valueOf((long) d);
                }
                yield String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            // 空白单元格返回空串（而非 null），使 isBlank() 校验语义一致，
            // 避免后续可选列（如班级）因 null 与空串混用导致判空分支不一致。
            case BLANK -> "";
            // ERROR 类型无法提取有效值，返回 null 触发必填列校验跳过该行
            default -> null;
        };
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** 导入结果摘要 */
    public record ImportResult(int imported, int skipped) {}

    /** 名单条目 + 提交状态 */
    public record RosterWithStatus(StudentRosterEntry entry, boolean submitted) {}
}
