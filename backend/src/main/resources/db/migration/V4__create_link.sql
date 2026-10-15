CREATE TABLE link
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    collection_id BIGINT        NOT NULL,
    original_url  VARCHAR(4096) NOT NULL,
    title         VARCHAR(500)  NOT NULL DEFAULT '',
    description   VARCHAR(2000) NOT NULL DEFAULT '',
    created_at    DATETIME(3)   NOT NULL,
    updated_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    -- [408] 这是"按集合翻链接"要用的索引：先按 collection_id 定位，
    -- 再按 created_at 倒序取最新的。索引列顺序必须和 WHERE、ORDER BY 的顺序对齐，
    -- 顺序颠倒索引就用不上。这个查询在 M6.2 做分页时才会真正吃满。
    KEY idx_link_collection_created (collection_id, created_at DESC),
    -- [业务] 链接随用户一起消失。
    -- [排错] InnoDB 会为外键列自动建索引，所以 user_id 上不用再手写 KEY，
    -- 按用户查链接时它已经被索引覆盖了。
    CONSTRAINT fk_link_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    -- [业务] 数据库这里允许级联删除，但业务规则要求"集合里还有链接就不许删集合"，
    -- 那道判断会在第 5 批加进 CollectionService。级联只是它背后的兜底。
    CONSTRAINT fk_link_collection FOREIGN KEY (collection_id) REFERENCES collection (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT = '用户收藏链接表';