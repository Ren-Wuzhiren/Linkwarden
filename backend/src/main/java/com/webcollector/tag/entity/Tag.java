package com.webcollector.tag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * [业务] 用户标签实体，对应 tag 表。
 *
 * [软件工程] 实体只负责「表结构 ↔ Java 对象」的映射，不承载业务规则。
 * 唯一性判断、所有权校验全部放在 Service 层，实体保持哑数据。
 */
@TableName("tag")
public class Tag {
    // [Java] @TableId 标记主键；type = AUTO 表示由数据库自增生成并回写。
    // [排错] 漏写 AUTO 时框架会先生成雪花 ID 再插入，自增列等于被绕过。

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
