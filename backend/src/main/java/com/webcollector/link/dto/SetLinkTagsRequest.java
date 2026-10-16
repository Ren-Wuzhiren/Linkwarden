package com.webcollector.link.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * [业务] 替换某条链接标签集合的请求体。
 *
 * [软件工程] 用 PUT 而不是 POST：语义是"整体替换成这一组"，
 * 不是"再追加一个标签"。空数组合法，表示清空全部标签。
 */
public record SetLinkTagsRequest (
        // [业务] 列表上的 @NotNull 拒绝 {"tagIds": null}，要求客户端明确表达意图；
        // 元素上的 @NotNull 拒绝 [1, null]，否则 null 会一路走到数据库层才报 NOT NULL。
        @NotNull(message = "标签列表不能为空，如需清空请传空数组")
        List<@NotNull(message = "标签 id 不能为 null") Long> tagIds
) {
}