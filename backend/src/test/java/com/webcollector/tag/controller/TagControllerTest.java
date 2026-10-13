package com.webcollector.tag.controller;

import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.GlobalExceptionHandler;
import com.webcollector.common.web.RequestIdFilter;
import com.webcollector.tag.dto.CreateTagRequest;
import com.webcollector.tag.dto.TagSummary;
import com.webcollector.tag.dto.UpdateTagRequest;
import com.webcollector.tag.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class TagControllerTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-11T12:00:00Z");

    private TagService tagService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tagService = mock(TagService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TagController(tagService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // [测试意图] 验证标签列表返回 200、统一响应、requestId 贯通和摘要字段。
    @Test
    void shouldReturnTagsWhenListing() throws Exception {
        when(tagService.listTags()).thenReturn(List.of(
                summary(1L, "Java"),
                summary(2L, "Network")
        ));

        mockMvc.perform(get("/api/v1/tags")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-001"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-tag-001"
                ))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("Java"))
                .andExpect(jsonPath("$.data[0].linkCount").value(0))
                .andExpect(jsonPath("$.data[0].createdAt")
                        .value("2026-10-11T12:00:00Z"))
                .andExpect(jsonPath("$.data[1].name")
                        .value("Network"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-001"));
    }

    // [测试意图] 验证创建标签返回 201、输入被 strip 规范化、消息为"标签创建成功"。
    @Test
    void shouldCreateTagWhenRequestIsValid() throws Exception {
        when(tagService.createTag(any(CreateTagRequest.class)))
                .thenReturn(summary(3L, "Java"));

        mockMvc.perform(post("/api/v1/tags")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-002"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Java  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.data.name").value("Java"))
                .andExpect(jsonPath("$.message")
                        .value("标签创建成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-002"));

        ArgumentCaptor<CreateTagRequest> captor =
                ArgumentCaptor.forClass(CreateTagRequest.class);

        verify(tagService).createTag(captor.capture());

        assertThat(captor.getValue().name()).isEqualTo("Java");
    }

    // [测试意图] 验证创建重名标签返回 409 与稳定错误码。
    @Test
    void shouldReturnConflictWhenTagNameExists() throws Exception {
        when(tagService.createTag(any(CreateTagRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.TAG_NAME_ALREADY_EXISTS
                ));

        mockMvc.perform(post("/api/v1/tags")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-003"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("TAG_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("标签名称已存在"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-003"));
    }

    // [测试意图] 验证改名返回 200、更新成功消息和新名称。
    @Test
    void shouldUpdateTagWhenRequestIsValid() throws Exception {
        when(tagService.updateTag(
                eq(9L),
                any(UpdateTagRequest.class)
        )).thenReturn(summary(9L, "JavaEE"));

        mockMvc.perform(patch("/api/v1/tags/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-004"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "JavaEE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name")
                        .value("JavaEE"))
                .andExpect(jsonPath("$.message")
                        .value("标签更新成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-004"));

        verify(tagService).updateTag(
                eq(9L),
                any(UpdateTagRequest.class)
        );
    }

    // [测试意图] 验证改不存在的标签返回 404 NOT_FOUND。
    @Test
    void shouldReturnNotFoundWhenUpdatingUnknownTag() throws Exception {
        when(tagService.updateTag(
                eq(99L),
                any(UpdateTagRequest.class)
        )).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(patch("/api/v1/tags/99")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-005"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Java"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-005"));
    }

    // [测试意图] 验证删除标签返回 200 和删除成功消息。
    @Test
    void shouldDeleteTag() throws Exception {
        mockMvc.perform(delete("/api/v1/tags/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-006"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("标签删除成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-006"));

        verify(tagService).deleteTag(9L);
    }

    // [测试意图] 验证未登录访问标签接口返回 401 AUTH_REQUIRED。
    @Test
    void shouldReturnUnauthorizedWhenNotLoggedIn() throws Exception {
        when(tagService.listTags())
                .thenThrow(new BusinessException(ErrorCode.AUTH_REQUIRED));

        mockMvc.perform(get("/api/v1/tags")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-007"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-007"));
    }

    // [测试意图] 验证纯空白名称返回 400，且根本不会调用 TagService。
    @Test
    void shouldReturnValidationErrorWhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/tags")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-tag-008"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.name")
                        .value("标签名称不能为空"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-tag-008"));

        verifyNoInteractions(tagService);
    }

    private static TagSummary summary(Long id, String name) {
        return new TagSummary(id, name, 0L, FIXED_INSTANT);
    }
}
