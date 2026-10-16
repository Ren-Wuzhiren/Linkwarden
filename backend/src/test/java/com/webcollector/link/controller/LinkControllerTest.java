package com.webcollector.link.controller;

import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.GlobalExceptionHandler;
import com.webcollector.common.web.RequestIdFilter;
import com.webcollector.link.dto.CreateLinkRequest;
import com.webcollector.link.dto.LinkSummary;
import com.webcollector.link.dto.SetLinkTagsRequest;
import com.webcollector.link.dto.UpdateLinkRequest;
import com.webcollector.link.service.LinkService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class LinkControllerTest {
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-15T12:00:00Z");
    private LinkService linkService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        linkService = mock(LinkService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new LinkController(linkService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // [测试意图] 验证链接列表返回 200、统一响应、requestId 贯通和摘要字段。
    @Test
    void shouldReturnLinksWhenListing() throws Exception {
        when(linkService.listLinks()).thenReturn(List.of(
                summary(1L, "https://example.com/a"),
                summary(2L, "https://example.com/b")
        ));

        mockMvc.perform(get("/api/v1/links")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-001"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-link-001"
                ))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].collectionId").value(5))
                .andExpect(jsonPath("$.data[0].originalUrl")
                        .value("https://example.com/a"))
                .andExpect(jsonPath("$.data[0].createdAt")
                        .value("2026-10-15T12:00:00Z"))
                .andExpect(jsonPath("$.data[1].originalUrl")
                        .value("https://example.com/b"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-001"));
    }

    // [测试意图] 验证保存链接返回 201、地址被 strip 规范化、消息为"链接保存成功"。
    @Test
    void shouldCreateLinkWhenRequestIsValid() throws Exception {
        when(linkService.createLink(any(CreateLinkRequest.class)))
                .thenReturn(summary(3L, "https://example.com/a"));

        mockMvc.perform(post("/api/v1/links")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-002"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "  https://example.com/a  ",
                                  "collectionId": 5,
                                  "title": "",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(3))
                .andExpect(jsonPath("$.message")
                        .value("链接保存成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-002"));

        ArgumentCaptor<CreateLinkRequest> captor =
                ArgumentCaptor.forClass(CreateLinkRequest.class);

        verify(linkService).createLink(captor.capture());

        assertThat(captor.getValue().url())
                .isEqualTo("https://example.com/a");
        assertThat(captor.getValue().collectionId()).isEqualTo(5L);
    }

    // [测试意图] 验证地址为空时在 HTTP 边界就被挡下，不会调用 Service。
    @Test
    void shouldReturnBadRequestWhenUrlIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "   ",
                                  "collectionId": 5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(linkService);
    }

    // [测试意图] 验证缺少 collectionId 时同样返回 400 VALIDATION_ERROR。
    @Test
    void shouldReturnBadRequestWhenCollectionIdIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/a"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"));

        verifyNoInteractions(linkService);
    }

    // [测试意图] 验证往别人的集合存链接返回 404 NOT_FOUND。
    @Test
    void shouldReturnNotFoundWhenCollectionNotOwned() throws Exception {
        when(linkService.createLink(any(CreateLinkRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://example.com/a",
                                  "collectionId": 99
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"));
    }

    // [测试意图] 验证读取单条链接返回 200 和完整摘要字段。
    @Test
    void shouldReturnLinkWhenGetting() throws Exception {
        when(linkService.getLink(9L)).thenReturn(
                summary(9L, "https://example.com/a")
        );

        mockMvc.perform(get("/api/v1/links/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-003"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9))
                .andExpect(jsonPath("$.data.updatedAt")
                        .value("2026-10-15T12:00:00Z"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-003"));
    }

    // [测试意图] 验证更新链接返回 200、更新成功消息和新的集合归属。
    @Test
    void shouldUpdateLinkWhenRequestIsValid() throws Exception {
        when(linkService.updateLink(
                eq(9L),
                any(UpdateLinkRequest.class)
        )).thenReturn(summary(9L, "https://example.com/a"));

        mockMvc.perform(patch("/api/v1/links/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-004"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collectionId": 7,
                                  "title": "新标题",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("链接更新成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-004"));

        ArgumentCaptor<UpdateLinkRequest> captor =
                ArgumentCaptor.forClass(UpdateLinkRequest.class);

        verify(linkService).updateLink(eq(9L), captor.capture());

        assertThat(captor.getValue().collectionId()).isEqualTo(7L);
        assertThat(captor.getValue().title()).isEqualTo("新标题");
    }

    // [测试意图] 验证改不存在的链接返回 404 NOT_FOUND。
    @Test
    void shouldReturnNotFoundWhenUpdatingUnknownLink() throws Exception {
        when(linkService.updateLink(
                eq(99L),
                any(UpdateLinkRequest.class)
        )).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(patch("/api/v1/links/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collectionId": 5,
                                  "title": "",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // [测试意图] 验证删除链接返回 200 和删除成功消息。
    @Test
    void shouldDeleteLink() throws Exception {
        mockMvc.perform(delete("/api/v1/links/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-005"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("链接删除成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-005"));

        verify(linkService).deleteLink(9L);
    }

    // [测试意图] 验证未登录访问链接接口返回 401 AUTH_REQUIRED。
    @Test
    void shouldReturnUnauthorizedWhenNotLoggedIn() throws Exception {
        when(linkService.listLinks())
                .thenThrow(new BusinessException(ErrorCode.AUTH_REQUIRED));

        mockMvc.perform(get("/api/v1/links")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-006"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-006"));
    }

    // [测试意图] 验证 PUT 设置标签返回 200 和"标签已更新"消息。
    @Test
    void shouldSetLinkTagsWhenRequestIsValid() throws Exception {
        mockMvc.perform(put("/api/v1/links/9/tags")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-link-007"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagIds": [1, 2]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("标签已更新"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-link-007"));

        ArgumentCaptor<SetLinkTagsRequest> captor =
                ArgumentCaptor.forClass(
                        SetLinkTagsRequest.class
                );

        verify(linkService).setLinkTags(eq(9L), captor.capture());

        assertThat(captor.getValue().tagIds())
                .containsExactly(1L, 2L);
    }

    // [测试意图] 验证给不存在的链接设置标签返回 404 NOT_FOUND。
    @Test
    void shouldReturnNotFoundWhenSettingTagsForUnknownLink() throws Exception {
        doThrow(
                new BusinessException(ErrorCode.NOT_FOUND)
        ).when(linkService).setLinkTags(
                eq(99L),
                any(SetLinkTagsRequest.class)
        );

        mockMvc.perform(put("/api/v1/links/99/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagIds": [1]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // [测试意图] 验证 tagIds 为 null 时判 400，不把 null 带进 Service。
    @Test
    void shouldReturnBadRequestWhenTagIdsIsNull() throws Exception {
        mockMvc.perform(put("/api/v1/links/9/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagIds": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(linkService);
    }

    // [测试意图] 验证 tagIds 里出现 null 元素时判 400——容器元素约束真的生效。
    // [排错] 这条不是"多测一条"：如果 List<@NotNull Long> 没被校验，null 会一路
    // 走到 link_tag 的 NOT NULL 列，把 400 变成 500。它是这条 DTO 契约唯一的证明。
    @Test
    void shouldReturnBadRequestWhenTagIdsContainsNull() throws Exception {
        mockMvc.perform(put("/api/v1/links/9/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagIds": [1, null]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(linkService);
    }

    private static LinkSummary summary(Long id, String url) {
        return new LinkSummary(
                id,
                5L,
                url,
                "",
                "",
                FIXED_INSTANT,
                FIXED_INSTANT
        );
    }
}
