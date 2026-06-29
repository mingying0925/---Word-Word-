# SkillBridge 技能桥

> 职业技能等级认定报名与文档自动化系统

实现 **"上传 Word 模板 → 自动解析书签 → 生成在线表单 → 学生填写 → 在原表上精准填空导出 Word"** 的完整闭环。

---

## 技术架构

| 层 | 技术 |
|---|------|
| **主服务** | Java 21 + Spring Boot 3.2 + Thymeleaf |
| **导出微服务** | Python 3.9+ + Flask + python-docx |
| **数据库** | MySQL 8.0（生产）/ H2（开发），Flyway 迁移管理 |
| **部署** | Docker Compose 一键部署 |
| **安全** | JWT HttpOnly Cookie、AES-256-GCM 数据加密、CSRF 防护、限流 |

## 核心功能

- **教师端** — 上传带书签的 .docx 模板，自动解析字段，生成在线表单；管理活动生命周期；查看学生填报数据并导出 Word
- **学生端** — 学号+身份证号登录，在线填写仿原表样式的表单，提交后不可修改
- **导出** — 精准保持原始表格样式、字体、排版，支持批量导出和异步任务
- **安全** — 身份证等敏感数据 AES-256-GCM 加密存储，角色分离鉴权

## 快速启动

```bash
# 终端 1 — Python 导出微服务
cd skillbridge-export
pip install -r requirements.txt
python app.py

# 终端 2 — Java 主服务
cd skillbridge-web
mvn spring-boot:run
```

访问 http://localhost:8080 进入门户，选择教师或学生入口。

## 技术栈

`Spring Boot` `Flask` `python-docx` `MySQL` `Docker` `JWT` `Thymeleaf` `Flyway`
