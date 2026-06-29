package com.skillbridge.service;

import com.skillbridge.model.StudentRosterEntry;
import com.skillbridge.model.Submission;
import com.skillbridge.repository.StudentRosterRepository;
import com.skillbridge.repository.SubmissionRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * StudentRosterService 单元测试。
 * 覆盖：Excel 导入、白名单校验、名单查询、提交状态统计、清空名单。
 */
class StudentRosterServiceTest {

    @Mock
    private StudentRosterRepository rosterRepository;
    @Mock
    private SubmissionRepository submissionRepository;

    @InjectMocks
    private StudentRosterService rosterService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** 构造一个包含表头 + 数据行的 Excel 字节数组 */
    private byte[] buildExcelBytes(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("学生名单");
            // 表头
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("学号");
            header.createCell(1).setCellValue("姓名");
            header.createCell(2).setCellValue("身份证号");
            header.createCell(3).setCellValue("班级");
            // 数据行
            for (int i = 0; i < rows.size(); i++) {
                var row = sheet.createRow(i + 1);
                String[] data = rows.get(i);
                for (int j = 0; j < data.length; j++) {
                    row.createCell(j).setCellValue(data[j]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    /* ===================== importFromExcel ===================== */

    @Nested
    @DisplayName("importFromExcel 从 Excel 导入名单")
    class ImportFromExcel {

        @Test
        @DisplayName("文件为空时抛出异常")
        void shouldThrowWhenFileEmpty() {
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rosterService.importFromExcel(1L, file));
            assertTrue(ex.getMessage().contains("上传"));
        }

        @Test
        @DisplayName("非 .xlsx 文件抛出异常")
        void shouldThrowWhenNotExcel() {
            MockMultipartFile file = new MockMultipartFile("file", "roster.csv",
                    "text/csv", "data".getBytes());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rosterService.importFromExcel(1L, file));
            assertTrue(ex.getMessage().contains(".xlsx"));
        }

        @Test
        @DisplayName("Excel 无数据行时抛出异常")
        void shouldThrowWhenNoDataRows() throws Exception {
            byte[] bytes = buildExcelBytes(Collections.emptyList());
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> rosterService.importFromExcel(1L, file));
            assertTrue(ex.getMessage().contains("没有数据行"));
        }

        @Test
        @DisplayName("成功导入有效数据行")
        void shouldImportValidRows() throws Exception {
            List<String[]> rows = Arrays.asList(
                    new String[]{"2024001", "张三", "440301199001011234", "计算机1班"},
                    new String[]{"2024002", "李四", "440301199002022345", "计算机1班"}
            );
            byte[] bytes = buildExcelBytes(rows);
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

            StudentRosterService.ImportResult result = rosterService.importFromExcel(1L, file);

            assertEquals(2, result.imported());
            assertEquals(0, result.skipped());
            verify(rosterRepository).deleteByActivityId(1L);
            verify(rosterRepository).saveAll(any());
        }

        @Test
        @DisplayName("跳过空行和同学号重复行")
        void shouldSkipBlankAndDuplicateRows() throws Exception {
            List<String[]> rows = Arrays.asList(
                    new String[]{"2024001", "张三", "440301199001011234", "计算机1班"},
                    new String[]{"", "", "", ""},                    // 空行
                    new String[]{"2024001", "重复学号", "440301999999999999", ""}, // 重复学号
                    new String[]{"2024002", "李四", "440301199002022345", null}
            );
            byte[] bytes = buildExcelBytes(rows);
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

            StudentRosterService.ImportResult result = rosterService.importFromExcel(1L, file);

            assertEquals(2, result.imported());
            assertEquals(2, result.skipped());
        }

        @Test
        @DisplayName("导入时保存正确的字段值")
        void shouldSaveCorrectFieldValues() throws Exception {
            List<String[]> rows = Collections.singletonList(
                    new String[]{"2024001", "张三", "440301199001011234", "计算机1班"}
            );
            byte[] bytes = buildExcelBytes(rows);
            MockMultipartFile file = new MockMultipartFile("file", "roster.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);

            rosterService.importFromExcel(1L, file);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<StudentRosterEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(rosterRepository).saveAll(captor.capture());
            List<StudentRosterEntry> saved = captor.getValue();
            assertEquals(1, saved.size());
            StudentRosterEntry e = saved.get(0);
            assertEquals(1L, e.getActivityId());
            assertEquals("2024001", e.getStudentId());
            assertEquals("张三", e.getStudentName());
            assertEquals("440301199001011234", e.getIdCard());
            assertEquals("计算机1班", e.getClassName());
        }
    }

    /* ===================== 白名单校验 ===================== */

    @Nested
    @DisplayName("白名单校验")
    class WhitelistCheck {

        @Test
        @DisplayName("名单为空时 isRosterEnabled 返回 false")
        void shouldReturnFalseWhenRosterEmpty() {
            when(rosterRepository.countByActivityId(1L)).thenReturn(0L);
            assertFalse(rosterService.isRosterEnabled(1L));
        }

        @Test
        @DisplayName("名单非空时 isRosterEnabled 返回 true")
        void shouldReturnTrueWhenRosterHasEntries() {
            when(rosterRepository.countByActivityId(1L)).thenReturn(5L);
            assertTrue(rosterService.isRosterEnabled(1L));
        }

        @Test
        @DisplayName("学生在名单中时返回条目")
        void shouldReturnEntryWhenStudentInRoster() {
            StudentRosterEntry entry = new StudentRosterEntry();
            entry.setStudentId("2024001");
            entry.setStudentName("张三");
            when(rosterRepository.findByActivityIdAndStudentIdAndIdCard(1L, "2024001", "440301199001011234"))
                    .thenReturn(Optional.of(entry));
            Optional<StudentRosterEntry> result = rosterService.checkWhitelist(1L, "2024001", "440301199001011234");
            assertTrue(result.isPresent());
            assertEquals("张三", result.get().getStudentName());
        }

        @Test
        @DisplayName("学生不在名单中时返回 empty")
        void shouldReturnEmptyWhenStudentNotInRoster() {
            when(rosterRepository.findByActivityIdAndStudentIdAndIdCard(1L, "9999999", "440301199001011234"))
                    .thenReturn(Optional.empty());
            Optional<StudentRosterEntry> result = rosterService.checkWhitelist(1L, "9999999", "440301199001011234");
            assertTrue(result.isEmpty());
        }
    }

    /* ===================== getRosterWithSubmissionStatus ===================== */

    @Nested
    @DisplayName("getRosterWithSubmissionStatus 名单 + 提交状态")
    class RosterWithStatus {

        @Test
        @DisplayName("名单为空时返回空列表")
        void shouldReturnEmptyListWhenRosterEmpty() {
            when(rosterRepository.findByActivityIdOrderByStudentIdAsc(1L))
                    .thenReturn(Collections.emptyList());
            List<StudentRosterService.RosterWithStatus> result =
                    rosterService.getRosterWithSubmissionStatus(1L);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("正确标记已提交和未提交的学生")
        void shouldMarkSubmittedAndPendingStudents() {
            StudentRosterEntry e1 = new StudentRosterEntry();
            e1.setStudentId("2024001");
            e1.setStudentName("张三");
            StudentRosterEntry e2 = new StudentRosterEntry();
            e2.setStudentId("2024002");
            e2.setStudentName("李四");
            when(rosterRepository.findByActivityIdOrderByStudentIdAsc(1L))
                    .thenReturn(Arrays.asList(e1, e2));

            Submission sub = new Submission();
            sub.setStudentId("2024001");
            when(submissionRepository.findByActivityId(1L))
                    .thenReturn(List.of(sub));

            List<StudentRosterService.RosterWithStatus> result =
                    rosterService.getRosterWithSubmissionStatus(1L);

            assertEquals(2, result.size());
            assertTrue(result.get(0).submitted());  // 张三已提交
            assertFalse(result.get(1).submitted()); // 李四未提交
        }
    }

    /* ===================== clearRoster ===================== */

    @Nested
    @DisplayName("clearRoster 清空名单")
    class ClearRoster {

        @Test
        @DisplayName("成功清空名单")
        void shouldClearRoster() {
            rosterService.clearRoster(1L);
            verify(rosterRepository).deleteByActivityId(1L);
        }
    }

    /* ===================== exportTemplate ===================== */

    @Nested
    @DisplayName("exportTemplate 导出模板")
    class ExportTemplate {

        @Test
        @DisplayName("导出的模板包含表头")
        void shouldExportTemplateWithHeader() throws Exception {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            rosterService.exportTemplate(out);
            byte[] bytes = out.toByteArray();
            assertTrue(bytes.length > 0);
            // 验证可被 POI 重新读取
            try (XSSFWorkbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
                var sheet = workbook.getSheetAt(0);
                var header = sheet.getRow(0);
                assertEquals("学号", header.getCell(0).getStringCellValue());
                assertEquals("姓名", header.getCell(1).getStringCellValue());
                assertEquals("身份证号", header.getCell(2).getStringCellValue());
                assertEquals("班级（可选）", header.getCell(3).getStringCellValue());
            }
        }
    }
}
