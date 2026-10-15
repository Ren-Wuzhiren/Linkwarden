package com.webcollector.link.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * [业务] 修改链接元数据的请求体。
 *
 * [业务] 这里刻意没有 url 字段：地址是链接的身份，改了地址就应该是一条新链接。
 * M4 之后每条链接都会绑定属于自己的快照资产，允许改地址会让资产和地址对不上。
 */
public record UpdateLinkRequest (
        @NotNull(message = "必须指定所属集合")
        Long collectionId,

        @Size(max = 500, message = "标题不能超过 500 个字符")
        String title,

        @Size(max = 2000, message = "描述不能超过 2000 个字符")
        String description
) {
    public UpdateLinkRequest {
        title = title == null ? "" : title.strip();
        description = description == null ? "" : description.strip();
    }
}