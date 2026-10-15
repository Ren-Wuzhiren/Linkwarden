package com.webcollector.link.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * [业务] 保存新链接的请求体。
 *
 * [软件工程] 结构上刻意不含 userId：所有者由服务端从会话推导，
 * 客户端只能"提出要存什么"，不能"声明这是谁的"。
 */
public record CreateLinkRequest (
        @NotBlank(message = "链接地址不能为空")
        @Size(max = 4096, message = "链接地址不能超过 4096 个字符")
        // [业务] 这一轮只校验"非空 + 长度"：scheme 白名单、SSRF 拦截和 URL 规范化
        // 分别排在 M2.5 与 M4.4，现在不提前写半套校验。
        String url,

        @NotNull(message = "必须指定所属集合")
        Long collectionId,

        // [软件工程] 校验上限必须和 DDL 对齐：title 是 VARCHAR(500)，description 是 VARCHAR(2000)。
        // 对不上时，要么放过数据库装不下的值（插入报 500），要么把存得下的值挡在门外。
        @Size(max = 500, message = "标题不能超过 500 个字符")
        String title,

        @Size(max = 2000, message = "描述不能超过 2000 个字符")
        String description
) {
    public CreateLinkRequest {
        // [业务] 粘贴来的地址常常带首尾空白和换行，先 strip 再交给校验。
        // [排错] 规范化必须放在紧凑构造器里：Bean Validation 在对象构造完成之后才运行，
        // 校验看到的就是 strip 之后的值。放到 Service 里做，长度校验就会用未规范化的值。
        url = url == null ? null : url.strip();

        // [Java] 非空列在 Java 侧用空串表达"未填写"，与 DDL 的 DEFAULT '' 对齐。
        title = title == null ? "" : title.strip();
        description = description == null ? "" : description.strip();
    }
}