package com.webcollector.tag.controller;

import com.webcollector.common.web.ApiResponse;
import com.webcollector.tag.dto.CreateTagRequest;
import com.webcollector.tag.dto.TagSummary;
import com.webcollector.tag.dto.UpdateTagRequest;
import com.webcollector.tag.service.TagService;
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
 * [软件工程] 标签接口的 HTTP 边界层：只做参数绑定、调用 Service、包装统一响应。
 *
 * 这里不判断任何业务规则——所有权、唯一性、时间都在 TagService 里，
 * Controller 保持薄，换框架或加新入口时不用重写规则。
 */
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ApiResponse<List<TagSummary>> listTags() {
        return ApiResponse.success(tagService.listTags());
    }

    // [业务] 创建成功返回 201 而不是 200，让客户端能明确区分"新建了资源"。
    @PostMapping
    public ResponseEntity<ApiResponse<TagSummary>> createTag(
            @Valid @RequestBody CreateTagRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        tagService.createTag(request),
                        "标签创建成功"
                ));
    }

    // [软件工程] 改名用 PATCH：这是局部更新，客户端不需要回传完整资源。
    @PatchMapping("/{id}")
    public ApiResponse<TagSummary> updateTag(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTagRequest request
    ) {
        return ApiResponse.success(
                tagService.updateTag(id, request),
                "标签更新成功"
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTag(@PathVariable Long id) {
        tagService.deleteTag(id);
        return ApiResponse.success(null, "标签删除成功");
    }
}
