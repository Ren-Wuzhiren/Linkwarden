# Web Collecting And Content Saving System

一个面向个人使用的 Web 收藏与内容保存系统。第一版目标是把一个 URL 稳定地保存为可检索、可回看的资料快照，包括网页元数据、HTML、PDF 和截图。

## 当前状态

- 阶段：X0 工程基线已完成，X1 用户与鉴权已完成，下一步进入 X2 收藏领域模型与 CRUD。
- 后端已建立 Maven 骨架、本地 profile、Flyway、统一响应与异常、健康检查、`app_user` 迁移、MyBatis-Plus 映射、BCrypt、Redis 持久化 Sa-Token 会话、注册、登录、当前用户和退出。
- 当前全量测试 86 项通过，其中 Testcontainers 集成测试使用真实 MySQL 8.4 和 Redis 7.4。
- 注册、登录、`/me` 和退出接口均已完成；真实会话生命周期已验证。
- 上游 web/worker 已在本地跑通，首页检查返回 HTTP 200；上游源码仍只用于研究和对照。
- 参考源码固定在 Linkwarden `v2.16.3`，只用于研究和对照，不直接进入本项目提交。

## 第一版范围

核心闭环：

1. 保存 URL。
2. 异步抓取网页并生成 HTML、PDF、截图。
3. 用集合和标签整理。
4. 通过标题、描述和正文文本搜索。
5. 打开详情页查看快照和阅读视图。

第一版明确不做：协作权限、公开分享、浏览器扩展、移动端、AI 标签、RSS、SSO、嵌套集合、MinIO、Meilisearch。

## 计划技术栈

- 后端：Java 17、Spring Boot 3.x、MyBatis-Plus、Sa-Token + Redis 会话、MySQL 8、Redis。
- 采集：Playwright Java、Jsoup/Readability4J、独立 worker profile。
- 前端：Vue 3、Vite、Pinia、Vue Router、Element Plus。
- 部署：Docker Compose、Nginx。

JWT 不作为第一版必需依赖；如果需要学习 JWT，应在鉴权闭环稳定后作为可替换实现单独评估。

## Maven 结构

根目录 `pom.xml` 是聚合工程，`backend/pom.xml` 是 Spring Boot 模块。IDEA 打开仓库根目录后，可以通过 Maven 工具窗口执行根工程构建。

## 目录

```text
AGENTS.md                 # 协作与学习规范
docs/                     # 需求、研读、工程化、复盘和四大件笔记
reference/linkwarden/     # 上游参考源码，本地克隆且不提交
backend/                  # Java 后端，业务代码由用户亲手实现
frontend/                 # 阶段 6 起由 Codex 生成并逐段讲解
deploy/                   # Docker、Nginx 和运行配置
```

## 参考源码

Linkwarden 使用 AGPL-3.0。参考源码仅用于理解问题、架构和设计取舍。若未来复制、翻译或分发上游代码，必须单独评估 AGPL 义务，并避免复用其品牌和原始资源。

## 文档入口

- [需求与总体方案](docs/需求文档/2026-09-12-001-项目总体方案与第一版范围.md)
- [Linkwarden 源码研读](docs/源码研读/README.md)
- [技术选型与架构](docs/工程化文档/04-技术选型与架构.md)
- [数据库设计](docs/工程化文档/05-数据库设计.md)
- [接口设计](docs/工程化文档/06-接口设计.md)
- [测试与验收交付](docs/工程化文档/07-测试与验收交付.md)
- [本地运行手册](docs/源码研读/06-本地运行手册.md)
