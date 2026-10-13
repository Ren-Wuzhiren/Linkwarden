package com.webcollector.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [业务] 创建标签的请求体。
 */
public record CreateTagRequest(
        @NotBlank(message = "标签名称不能为空")
        @Size(max = 64, message = "标签名称不能超过 64 个字符")
        String name
) {
    /**
     * [业务] 规范化必须发生在 Bean Validation 之前。
     *
     * [Java] record 的紧凑构造器只能给参数赋值，编译器会自动写回字段；
     * 写 this.name = ... 会编译不过。
     *
     * [排错] strip() 而不是 trim()：trim() 删不掉全角空格 U+3000，
     * 用户从中文文档里粘贴时会带进来。
     */
    public CreateTagRequest {
        name = name == null ? null : name.strip();
    }
}
