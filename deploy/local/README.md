# 本地开发依赖

该 Compose 只提供自有项目需要的 MySQL 8.4 和 Redis 7.4，不包含上游 Linkwarden 服务。

```powershell
docker compose -f deploy/local/docker-compose.yml up -d
docker compose -f deploy/local/docker-compose.yml ps
```

默认端口：

- MySQL：`localhost:3307`
- Redis：`localhost:6380`

未创建 `.env` 时使用仅供本地开发的默认密码；正式部署必须通过环境变量覆盖。停止服务但保留数据卷：

```powershell
docker compose -f deploy/local/docker-compose.yml down
```

不要添加 `-v`，否则会删除数据库和 Redis 数据卷。

