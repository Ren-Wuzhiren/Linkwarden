package com.webcollector.tag.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

/**
 * [业务] 标签的对外摘要，只暴露安全字段。
 *
 * [软件工程] 用 record 表达不可变响应契约；实体 Tag 绝不直接作为 HTTP 响应返回，
 * 否则将来给实体加内部字段会无声地泄漏到接口上。
 */
public record TagSummary(
        Long id,
        String name,
        // [业务] linkCount 需要 link_tag 表才能算出真实值，而它在 M2.4 才落地；
        // 这里先固定 0，与 CollectionSummary.linkCount 的处理方式保持一致。
        long linkCount,

        // [排错] Instant 在 standalone MockMvc 下会被序列化成数字时间戳，
        // 显式声明为 STRING 才能稳定输出 ISO 8601。
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {
}
