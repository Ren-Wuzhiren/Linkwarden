-- [软件工程] Flyway 约定：V<版本>__<描述>.sql，版本号与描述之间是两个下划线。
-- [排错] 已执行过的迁移永不修改，结构变化一律新增更高版本文件。
CREATE TABLE tag
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    name       VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    -- [业务] 唯一约束的粒度是「用户 + 名称」，所以不同用户可以拥有同名标签。
    -- [排错] 这个索引是并发下的最终防线，业务层的"先查再插"不能替代它。
    UNIQUE KEY uk_tag_user_name (user_id, name),
    -- [业务] 用户注销时标签随之消失，与 collection 保持同一种级联语义。
    CONSTRAINT fk_tag_user
        FOREIGN KEY (user_id) REFERENCES app_user (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '用户标签表';