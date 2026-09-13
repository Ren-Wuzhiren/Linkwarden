# Backend

后端目录预留给阶段 1 及之后的 Java 代码。

实现约定：

- Java 17 + Spring Boot 3.x + MyBatis-Plus。
- API 与 Worker 使用同一代码库的不同 profile。
- 后端业务代码由用户亲手实现，Codex 先讲解再审查。
- 开始编码前先阅读 `docs/工程化文档/04-技术选型与架构.md`、`05-数据库设计.md` 和 `06-接口设计.md`。

当前已创建 Maven 骨架，但尚未实现任何鉴权业务。

## 当前结构

```text
backend/
  pom.xml
  src/main/java/com/webcollector/WebCollectorApplication.java
  src/main/resources/application.yml
```

## 已锁定依赖

- Java 17、Spring Boot 3.5.3
- MyBatis-Plus 3.5.17
- Sa-Token 1.46.0
- MySQL、Flyway、Redis
- Spring Boot Actuator、springdoc OpenAPI
- Spring Boot Test、Testcontainers

阶段 1.2 开始由用户亲手实现 profile、数据源、Flyway、统一响应与健康检查。

