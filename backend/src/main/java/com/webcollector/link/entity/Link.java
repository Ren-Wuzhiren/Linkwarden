package com.webcollector.link.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;


import java.time.LocalDateTime;

/**
 * [业务] 用户收藏的链接实体，对应 link 表。
 *
 * [软件工程] 和 Tag 一样，实体只负责「表结构 ↔ Java 对象」的映射：
 * 所有权校验、集合归属判断全部放在 LinkService，实体保持哑数据。
 */
@TableName("link")
public class Link {
    // [Java] @TableId 标记主键；type = AUTO 表示由数据库自增生成并回写。
    // [排错] 漏写 AUTO 时框架会先生成雪花 ID 再插入，自增列等于被绕过。
    @TableId(type = IdType.AUTO)
    private Long id;

    // [业务] user_id 是所有权根字段，永远由服务端从会话推导，不接受客户端传入。
    private Long userId;

    // [业务] collection_id 指向"我的某个集合"；写入前必须先校验集合归属。
    private Long collectionId;

    // [业务] 用户粘贴进来的原始地址，原样保存，这一轮不做任何规范化。
    private String originalUrl;

    // [Java] 非空列在 Java 侧用空串表达"未填写"，与 DDL 的 DEFAULT '' 对齐。
    private String title;

    private String description;

    // [Java] LocalDateTime 对应 DATETIME(3)，毫秒精度；UTC 语义由业务代码负责。
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
