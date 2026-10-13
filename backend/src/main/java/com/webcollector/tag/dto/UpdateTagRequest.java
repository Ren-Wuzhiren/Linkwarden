package com.webcollector.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [业务] 修改标签名称的请求体。
 */
public record UpdateTagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 64, message = "标签名称不能超过 64 个字符")
        String name
) {
    public UpdateTagRequest {
        name = name == null ? null : name.strip();
    }
}
