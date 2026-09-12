# Linkwarden v2.16.3 API 与页面入口

## API 组织

上游使用 Next.js Pages Router 的 `pages/api/v1`：

- 路由文件只处理方法分派、请求参数转换和响应。
- 权限通常由 `verifyUser` 统一完成。
- 业务逻辑放在 `apps/web/lib/api/controllers/`。
- 公共资源使用独立的 `/api/v1/public/*` 路径。

## 第一版需要对应的上游接口

- `POST /api/v1/session`：用户名密码登录并创建会话 Token。
- `GET/POST /api/v1/collections`：查询和创建集合。
- `PUT/DELETE /api/v1/collections/{id}`：修改和删除集合。
- `GET/POST /api/v1/links`：查询和创建链接，支持游标分页。
- `GET/PUT/DELETE /api/v1/links/{id}`：单链接读取、修改和删除。
- `POST /api/v1/links/archive`：批量重新保存。
- `GET /api/v1/search`：使用普通文本、字段过滤和否定条件搜索。
- `GET/POST/DELETE /api/v1/tags`：标签管理。
- `GET /api/v1/archives/{linkId}`：获取保存资产。
- `GET /api/v1/worker`：查看 Worker 状态。

## 搜索语法

上游支持结构化搜索字段：`url`、`name`、`description`、`type`、`collection`、`pinned`、`public`、`before`、`after`、`tag`。字段前加 `!` 表示否定，例如：

```text
java tag:spring before:2026-09-01 !collection:archive
```

第一版 Java 版本可以先支持普通文本、`tag:`、`collection:` 和 `before/after`，其余字段后置。

## 主要页面

- 登录与注册页面。
- 仪表盘与集合导航。
- 链接列表、磁贴、列表视图和分页。
- 链接详情、原网页、Readable、PDF、截图和 Monolith 视图。
- 标签列表与标签详情。
- 搜索页。
- 保存链接、编辑链接和批量操作弹窗。

## 我们的接口调整

- 自有 API 统一使用 `/api/v1`，返回结构保持简单：`data`、`message`、`requestId`。
- 单资源使用 ID 路径，不照搬上游的批量 `PUT/DELETE` 行为作为第一版必需项。
- 增加 `GET /api/v1/jobs/{id}`，让前端明确展示异步保存进度和失败原因。
- 增加 `GET /api/v1/assets/{id}`，按鉴权结果返回资产元数据或文件流。

