package com.webcollector.collection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateCollectionRequest (
        @NotBlank(message = "集合名称不能为空")
        @Size(max = 100, message = "集合名称不能超过 100 个字符")
        String name,

        @Size(max = 500, message = "集合描述不能超过 500 个字符")
        String description
) {
    public UpdateCollectionRequest {
        name = name == null ? null : name.strip();
        description = description == null ? "" : description.strip();
    }
}
