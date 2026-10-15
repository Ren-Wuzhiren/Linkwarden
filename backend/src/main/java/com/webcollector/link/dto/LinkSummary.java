package com.webcollector.link.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * [业务] 链接的对外摘要，只暴露安全字段。
 *
 * [软件工程] 和 TagSummary 一样用 record 表达不可变响应契约；
 * 实体 Link 绝不直接作为 HTTP 响应返回，否则将来给实体加内部字段会无声地泄漏到接口上。
 */
public record LinkSummary (
        Long id,
        Long collectionId,

        // [业务] 字段名带 original 前缀，是为了给 M2.5 的 normalizedUrl 留出位置：
        // url（客户端提交的）→ originalUrl（原样存下的）→ normalizedUrl（归一化后的）。
        String originalUrl,

        String title,
        String description,

        // [排错] Instant 在 standalone MockMvc 下会被序列化成数字时间戳，
        // 显式声明为 STRING 才能稳定输出 ISO 8601。
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt,

        // [业务] 这里比 TagSummary 多暴露一个 updatedAt：链接的标题、描述和所属集合都可编辑，
        // 客户端要能展示"最近修改时间"；标签只有名字，createdAt 就够了。
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant updatedAt
) {

}
