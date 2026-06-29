# SkillBridge 使用指南

> **版本**：V3.1
> **最后更新**：2026-06-24
> **面向读者**：开发者、部署人员
> **适用环境**：开发环境（端口 8080）/ 生产环境（端口 8088）
>
> **相关文档**：
> - [教师使用说明书](教师使用说明书.md) — 面向教师的完整操作指南
> - [Word 导入功能使用说明书](doc/Word导入功能使用说明书.md) — Word 模板制作与导入详细说明
> - [学生使用说明书](学生使用说明书.md) — 面向学生的操作指南
> - [PRD](prd.md) — 产品需求文档
> - [启动方式](deploy/启动方式.md) — Windows 服务器启动命令

SkillBridge 是一套职业技能等级认定报名与文档自动化系统，支持教师上传 Word 模板、自动生成在线表单、学生填写后导出 Word 的完整闭环。

---

## 目录

1. [快速启动](#一快速启动)
2. [核心功能](#二核心功能)
3. [Word 模板制作](#三word-模板制作)
4. [学生名单导入](#四学生名单导入)
5. [测试](#五测试)
6. [项目结构](#六项目结构)
7. [配置说明](#七配置说明)
8. [安全特性](#八安全特性)
9. [常见问题](#九常见问题)
10. [相关文档](#十相关文档)

---

## 一、快速启动

### 1.1 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 21+ | Spring Boot 3.2.5 主服务 |
| Python | 3.9+ | Flask 导出微服务（python-docx） |
| Maven | 3.8+ | Java 依赖管理 |
| MySQL | 8.0 | 生产数据库（开发可用 H2 内存库） |

### 1.2 开发环境启动

```bash
# 终端 1 — Python Flask (端口 5000)
cd D:\development\SkillBridge\skillbridge-export
pip install -r requirements.txt
python app.py

# 终端 2 — Java Spring Boot (端口 8080)
cd D:\development\SkillBridge\skillbridge-web
mvn spring-boot:run
```

启动后访问：

| 服务 | 开发环境地址 | 生产环境地址 |
|------|-------------|-------------|
| Java 主站 | http://localhost:8080 | http://<服务器IP>:8088 |
| Python API | http://localhost:5000 | http://127.0.0.1:5000（仅内网） |
| 教师入口 | http://localhost:8080/teacher/login | http://<服务器IP>:8088/teacher/login |
| 学生入口 | http://localhost:8080/student/login | http://<服务器IP>:8088/student/login |

> **环境区分**：开发环境使用 H2 内存数据库、端口 8080；生产环境使用 MySQL、端口 8088。详见 [配置说明](#七配置说明)。

### 1.3 默认账号

| 环境 | 角色 | 账号 | 密码 |
|------|------|------|------|
| 开发环境 | 教师 | `admin` | `admin123` |
| 生产环境 | 教师 | `admin` | 由环境变量 `TEACHER_DEFAULT_PASSWORD` 设置，首次登录后请尽快修改 |

> 生产环境密码设置与修改方式请参考 [教师使用说明书 - 日常运维](教师使用说明书.md#七日常运维)。

### 1.4 生产环境部署

生产环境使用 MySQL 数据库，配置文件位于 `skillbridge-web/src/main/resources/application-prod.yml`。

```bash
# 打包
cd D:\development\SkillBridge\skillbridge-web
mvn clean package -DskipTests

# 运行（指定生产配置）
java -jar target/skillbridge-web-1.0.0.jar --spring.profiles.active=prod
```

---

## 二、核心功能

### 2.1 教师端

| 功能 | 路径 | 说明 |
|------|------|------|
| 仪表盘 | `/teacher/dashboard` | 统计概览、待办事项、快捷操作 |
| 创建活动 | `/teacher/create` | 上传 Word 模板或从模板库选择 |
| 确认字段 | `/teacher/create/confirm` | 配置字段显示名、必填、启用 |
| 活动列表 | `/teacher/activities` | 搜索筛选、查看提交/名单数、截止提醒 |
| 学生名单 | `/teacher/activity/{id}/roster` | Excel 批量导入、白名单校验 |
| 提交列表 | `/teacher/activity/{id}/submissions` | 查看、导出 Word/Excel/ZIP |
| 模板库 | `/teacher/templates` | 保存/上传/复用 Word 模板 |
| 导出任务 | `/teacher/export-tasks` | 异步导出任务列表与下载 |
| 账号管理 | `/teacher/accounts` | 创建/禁用/重置密码 |
| 审计日志 | `/teacher/audit-logs` | 操作记录追踪 |

### 2.2 学生端

| 功能 | 路径 | 说明 |
|------|------|------|
| 登录 | `/student/login` | 学号 + 身份证号 |
| 填写表单 | `/student/activity/{id}/form` | 在线填写（自动保存草稿） |
| 查看提交 | `/student/activity/{id}/view-submission` | 查看已提交内容 |
| 下载 Word | `/student/activity/{id}/download` | 下载自己的申报表 |
| 提交成功 | `/student/activity/{id}/success` | 提交成功页 |

---

## 三、Word 模板制作

### 3.1 插入书签

1. 用 Word 打开申报表模板（`.docx`）
2. 选中要填写的位置
3. 菜单 `插入` → `书签`
4. 输入英文名称（如 `name`、`idCard`）
5. 点击 `添加`

### 3.2 批量添加书签

```bash
cd D:\development\SkillBridge\skillbridge-export
python insert_bookmarks.py --help
```

### 3.3 注意事项

- 书签名必须为英文（字母、数字、下划线）
- 文件格式必须为 `.docx`（不支持 `.doc`）
- 文件大小不超过 10MB
- 一个书签只能出现一次（多位置请用不同名称）

---

## 四、学生名单导入

### 4.1 Excel 格式

| 学号 | 姓名 | 身份证号 | 班级（可选） |
|------|------|----------|-------------|
| 2026001 | 张三 | 110101200001011234 | 计算机1班 |

- 第一行为表头（将被跳过）
- 后续行依次为：学号 | 姓名 | 身份证号 | 班级（可选）
- 文件大小不超过 10MB，仅支持 `.xlsx` 格式

### 4.2 白名单模式

导入名单后，只有名单内的学生可以提交报名。如不导入名单，所有学生均可报名。

---

## 五、测试

### 5.1 运行全部测试

```bash
cd D:\development\SkillBridge\skillbridge-web
mvn test
```

### 5.2 测试覆盖

- 单元测试：Service 层、Utils、Config
- 控制器测试：`@WebMvcTest` 隔离测试
- 集成测试：`@SpringBootTest` 端到端流程
- 当前测试数：226 个，0 失败

---

## 六、项目结构

```
SkillBridge/
├── prd.md                          # 产品需求文档
├── 教师使用说明书.md                 # 教师使用说明书
├── USAGE.md                        # 本文件
├── skillbridge-web/                # Java Spring Boot 主服务
│   ├── src/main/java/com/skillbridge/
│   │   ├── controller/             # 控制器（Teacher/Student/Index/Template）
│   │   ├── service/                # 服务层（Activity/Teacher/Student/Roster/Export）
│   │   ├── repository/             # JPA 仓库
│   │   ├── model/                  # 实体模型
│   │   ├── config/                 # 配置（CSRF/限流/i18n/TraceId）
│   │   ├── interceptor/            # JWT 拦截器
│   │   └── utils/                  # 工具类（加密/JWT/Cookie）
│   ├── src/main/resources/
│   │   ├── templates/              # Thymeleaf 模板
│   │   ├── static/css/             # 全局样式（Morandi 设计系统）
│   │   ├── messages_*.properties   # i18n 国际化消息
│   │   └── db/migration/           # Flyway 数据库迁移
│   └── src/test/                   # 测试代码
├── skillbridge-export/             # Python Flask 导出微服务
│   ├── app.py                      # Flask 主应用
│   └── insert_bookmarks.py         # 书签批量插入工具
└── doc/                            # 文档
```

---

## 七、配置说明

### 7.1 开发配置（application-dev.yml）

- 数据库：H2 内存库
- 端口：8080
- 日志级别：DEBUG

### 7.2 生产配置（application-prod.yml）

- 数据库：MySQL 8.0
- 端口：8088（或自定义）
- 日志级别：INFO
- JWT 密钥外部化

### 7.3 关键配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `app.upload-dir` | `${user.dir}/uploads` | 文件上传目录 |
| `app.teacher.default-id` | `admin` | 默认教师工号 |
| `app.teacher.default-password` | `admin123` | 默认教师密码 |
| `app.export.python-url` | `http://127.0.0.1:5000` | Python 微服务地址 |

---

## 八、安全特性

| 特性 | 说明 |
|------|------|
| JWT 鉴权 | HttpOnly Cookie，SameSite=Lax |
| CSRF 防护 | 所有 POST/PUT/DELETE 请求需携带 `_csrf` token |
| 限流 | 登录接口限流（防暴力破解） |
| 身份证加密 | AES-256-GCM 加密存储 |
| 密码哈希 | BCrypt 加密 |
| 审计日志 | 关键操作记录（登录/创建/删除/导出） |
| TraceId | 每个请求分配唯一追踪 ID |

---

## 九、常见问题

### 启动失败

1. **端口被占用**：检查 8080/5000 端口是否被其他程序占用
2. **Python 依赖缺失**：`pip install -r requirements.txt`
3. **数据库连接失败**：检查 MySQL 是否启动，配置是否正确

### Word 导出失败

1. 检查 Python 微服务是否运行：`curl http://127.0.0.1:5000/health`
2. 检查模板文件是否存在：`uploads/` 目录
3. 查看日志：`skillbridge-web/logs/`

### 测试失败

1. 确保使用 Java 21+
2. 确保 Maven 依赖完整：`mvn clean install`
3. H2 数据库会自动创建，无需手动配置

---

## 十、相关文档

| 文档 | 说明 | 面向读者 |
|------|------|---------|
| [prd.md](prd.md) | 产品需求文档（PRD） | 开发者、产品 |
| [教师使用说明书.md](教师使用说明书.md) | 教师端完整操作指南 | 教师 |
| [学生使用说明书.md](学生使用说明书.md) | 学生端操作指南 | 学生 |
| [doc/Word导入功能使用说明书.md](doc/Word导入功能使用说明书.md) | Word 模板制作与书签插入详细说明 | 教师、部署人员 |
| [deploy/启动方式.md](deploy/启动方式.md) | Windows 服务器启动命令 | 部署人员 |
