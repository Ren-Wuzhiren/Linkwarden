package com.webcollector.link.controller;

import com.webcollector.common.web.ApiResponse;
import com.webcollector.link.dto.CreateLinkRequest;
import com.webcollector.link.dto.LinkSummary;
import com.webcollector.link.dto.UpdateLinkRequest;
import com.webcollector.link.service.LinkService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * [软件工程] 链接接口的 HTTP 边界层：只做参数绑定、调用 Service、包装统一响应。
 *
 * 这里不判断任何业务规则——所有权、集合归属和时间都在 LinkService 里，
 * Controller 保持薄，换框架或加新入口时不用重写规则。
 */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {
    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @GetMapping
    public ApiResponse<List<LinkSummary>> listLinks() {
        return ApiResponse.success(linkService.listLinks());
    }

    // [业务] 创建成功返回 201 而不是 200，让客户端能明确区分"新建了资源"。
    @PostMapping
    public ResponseEntity<ApiResponse<LinkSummary>> createLink(
            @Valid @RequestBody CreateLinkRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        linkService.createLink(request),
                        "链接保存成功"
                ));
    }

    @GetMapping("/{id}")
    public ApiResponse<LinkSummary> getLink(@PathVariable Long id) {
        return ApiResponse.success(linkService.getLink(id));
    }

    // [软件工程] 改元数据用 PATCH：局部更新，客户端不需要回传完整资源。
    @PatchMapping("/{id}")
    public ApiResponse<LinkSummary> updateLink(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLinkRequest request
    ) {
        return ApiResponse.success(
                linkService.updateLink(id, request),
                "链接更新成功"
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteLink(@PathVariable Long id) {
        linkService.deleteLink(id);
        return ApiResponse.success(null, "链接删除成功");
    }
}
