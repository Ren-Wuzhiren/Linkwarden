# Web Collecting And Content Saving System

一个面向个人使用的 Web 收藏与内容保存系统。第一版目标是把一个 URL 稳定地保存为可检索、可回看的资料快照，包括网页元数据、HTML、PDF 和截图。

## 当前状态

- 阶段：仓库初始化、Linkwarden 源码研读和自有项目设计文档均已完成。
- 上游 web/worker 已在本地跑通，首页检查返回 HTTP 200。
- 自有 Java/Vue 实现尚未开始编码；下一步是阶段 1 的 Spring Boot 骨架、数据库迁移、用户与鉴权。
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

- 后端：Java 17、Spring Boot 3.x、MyBatis-Plus、Sa-Token/JWT、MySQL 8、Redis。
- 采集：Playwright Java、Jsoup/Readability4J、独立 worker profile。
- 前端：Vue 3、Vite、Pinia、Vue Router、Element Plus。
- 部署：Docker Compose、Nginx。

## 目录

```text
AGENTS.md                 # 协作与学习规范
docs/                     # 需求、研读、工程化、复盘和四大件笔记
reference/linkwarden/     # 上游参考源码，本地克隆且不提交
backend/                  # 后续由用户亲手实现
frontend/                 # 后续由 Codex 生成并逐段讲解
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
