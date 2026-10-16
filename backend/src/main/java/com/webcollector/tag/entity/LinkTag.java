package com.webcollector.tag.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * [业务] 链接与标签的关联，对应 link_tag 表。
 *
 * [软件工程] 这是纯关联表，没有自己的业务标识，也不承载任何规则：
 * "链接归我""标签归我"的校验全部在 LinkService，实体保持哑数据。
 */
@TableName("link_tag")
public class LinkTag {
    // [排错] 这张表的主键是 (link_id, tag_id) 联合主键，而 MyBatis-Plus 只认单主键。
    // 不声明 @TableId 时框架启动会打 WARN："Can not find table primary key in Class"。
    // 这里把 link_id 声明成逻辑主键，只为让 TableInfo 初始化安静下来。
    //
    // [排错] 代价是这张表**绝不能使用 selectById / deleteById / updateById**：
    // 它们都以 link_id 作为唯一标识来匹配，deleteById(tagId) 会把该标签的
    // 全部关联一次性删光。本表所有条件一律走 LambdaQueryWrapper。
    @TableId(value = "link_id", type = IdType.INPUT)
    private Long linkId;

    private Long tagId;

    // [Java] 关联的建立时间。列声明为 NOT NULL 且没有默认值，必须由业务代码写入。
    private LocalDateTime createdAt;

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
