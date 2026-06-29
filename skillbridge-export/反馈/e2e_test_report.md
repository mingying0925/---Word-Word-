# ============================================================
# E2E 测试报告 — 技能等级认定个人申报表 MVP
# 测试时间: 2026-06-18 10:30
# ============================================================

## 步骤一：上传模板创建活动
✅ 成功
- 书签模板已上传并通过教师端创建活动
- Java 后台解析书签 JSON，前端动态生成表单

## 步骤二：表单渲染（46/47 个控件）
✅ 接近完整
- 渲染出 46 个表单控件（预期 47，因 signature 和 sign_date 在同一单元格，只渲染了 sign_date）
- 类型推断正确：性别 → radio（男/女）、出生日期/发证时间 → date、其他 → text
- 工作经历 5 行 × 4 列 = 20 个字段正确渲染
- 未渲染的字段：`signature`（与 sign_date 共用单元格）

## 步骤三：学生填写提交
✅ 成功
- 学号 2024001 + 身份证 440301199001011234 登录成功
- 提交 46 个字段数据，含工作经历 2 行数据（第 3-5 行留空）
- 重复提交正确拦截

## 步骤四：导出 Word 验证
✅ 成功（导出 22073 字节）

### 字段填充验证（从导出的 docx 中读取）
| 书签 | 期望值 | 结果 |
|---|---|---|
| name | 张三 | ✅ |
| gender | 男 | ✅ |
| birth_date | 1990-01-01 | ✅ |
| id_type | 居民身份证 | ✅ |
| id_number | 440301199001011234 | ✅ |
| phone | 13800138000 | ✅ |
| candidate_type | 企业员工（用人单位报考） | ✅ |
| highest_education | 本科 | ✅ |
| education_major | 电气工程 | ✅ |
| education_date | 1990-01-01 | ✅ |
| work_unit | 广州科技有限公司 | ✅ |
| current_position | 电气工程师 | ✅ |
| declare_occupation | 电工 | ✅ |
| declare_level | 高级（三级） | ✅ |
| exam_type | 正考 | ✅ |
| declare_condition | 累计从事本职业工作6年 | ✅ |
| current_cert_type | 技能等级证书 | ✅ |
| cert_level | 中级（四级） | ✅ |
| cert_occupation | 电工 | ✅ |
| cert_number | S000012345678 | ✅ |
| cert_date | 1990-01-01 | ✅ |
| title_type | 职业资格 | ✅ |
| title_name | 助理工程师 | ✅ |
| title_number | Z12345678 | ✅ |
| title_date | 1990-01-01 | ✅ |
| work_start_1 | 2020-01 至 2022-12 | ✅ |
| work_company_1 | 第一家公司 | ✅ |
| work_position_1 | 工程师 | ✅ |
| work_contact_1 | 王工 13800000001 | ✅ |
| work_start_2 | 2023-01 至 2024-12 | ✅ |
| work_company_2 | 第二家公司 | ✅ |
| work_position_2 | 高级工程师 | ✅ |
| work_contact_2 | 王工 13800000002 | ✅ |
| work_start_3~5 | (留空) | ✅ |
| work_company_3~5 | (留空) | ✅ |
| work_position_3~5 | (留空) | ✅ |
| work_contact_3~5 | (留空) | ✅ |
| signature | (未提交 - 见下方说明) | ⚠️ |
| sign_date | 1990-01-01 | ✅（覆盖了 signature 单元格） |

## 步骤五：结论

### MVP 闭环判定：✅ 成功（有 1 个已知限制）

### 已知问题
1. **signature 和 sign_date 共享单元格**: 书签在同一单元格内，表单只渲染第一个（sign_date），用户无法输入签名。导出时 sign_date 值覆盖了整个单元格。
   - 修复方案：修改 form.html，当 `bookmarkNames` 包含多个书签时，为每个书签渲染独立控件；或调整模板，将签名和日期放入不同单元格。

2. **表格样式未验证**: 终端输出的表格结构因编码问题无法完全可视化验证，建议直接打开导出的 test_export_2.docx 查看。

### 输出文件
- `D:\development\SkillBridge\skillbridge-export\test_export_2.docx` — 导出的填好数据后的文档
