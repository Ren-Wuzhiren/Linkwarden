-- [软件工程] Flyway 约定：V<版本>__<描述>.sql，版本号与描述之间是两个下划线。
-- [排错] 已执行过的迁移永不修改，结构变化一律新增更高版本文件。
CREATE TABLE link_tag
(
    link_id    BIGINT      NOT NULL,
    tag_id     BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL,
    -- [408] 联合主键一次承担三件事：禁止同一对 (link_id, tag_id) 出现两次；
    -- 给 link_id 提供最左前缀索引，"查某条链接挂了哪些标签"直接走它；
    -- 让关联表不需要任何代理主键。
    PRIMARY KEY (link_id, tag_id),
    -- [业务] 链接被删除时，它身上的关联跟着消失，业务层不用手工清理。
    CONSTRAINT fk_link_tag_link FOREIGN KEY (link_id) REFERENCES link (id) ON DELETE CASCADE,
    -- [业务] 标签被删除时只解除关联，链接本身必须活着——这就是 M2.4 验收标准
    -- "删标签只解除关联不删链接"的实现位置。它也是本表唯一一条业务语义。
    -- [排错] InnoDB 会为 tag_id 自动建索引（联合主键的最左列是 link_id，
    -- tag_id 用不上它），所以这里不必再手写 KEY。
    CONSTRAINT fk_link_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '链接与标签的关联表';