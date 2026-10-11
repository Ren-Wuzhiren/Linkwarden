package com.webcollector.collection.controller;

import com.webcollector.collection.dto.CollectionSummary;
import com.webcollector.collection.dto.CreateCollectionRequest;
import com.webcollector.collection.dto.UpdateCollectionRequest;
import com.webcollector.collection.service.CollectionService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.GlobalExceptionHandler;
import com.webcollector.common.web.RequestIdFilter;
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

class CollectionControllerTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-11T12:00:00Z");

    private CollectionService collectionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        collectionService = mock(CollectionService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new CollectionController(collectionService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // [测试意图] 验证集合列表返回 200、统一响应和集合摘要字段。
    @Test
    void shouldReturnCollectionsWhenListing() throws Exception {
        when(collectionService.listCollections())
                .thenReturn(List.of(
                        summary(
                                1L,
                                "Inbox",
                                "",
                                true
                        ),
                        summary(
                                2L,
                                "Reading",
                                "my reading list",
                                false
                        )
                ));

        mockMvc.perform(get("/api/v1/collections")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-001"
                        ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-collection-001"
                ))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name")
                        .value("Inbox"))
                .andExpect(jsonPath("$.data[0].isInbox")
                        .value(true))
                .andExpect(jsonPath("$.data[0].linkCount")
                        .value(0))
                .andExpect(jsonPath("$.data[1].name")
                        .value("Reading"))
                .andExpect(jsonPath("$.data[1].isInbox")
                        .value(false))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-001"));
    }

    // [测试意图] 验证创建集合返回 201、规范化输入和创建成功消息。
    @Test
    void shouldCreateCollectionWhenRequestIsValid() throws Exception {
        when(collectionService.createCollection(
                any(CreateCollectionRequest.class)
        )).thenReturn(summary(
                2L,
                "Reading",
                "my reading list",
                false
        ));

        mockMvc.perform(post("/api/v1/collections")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-002"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  Reading  ",
                                  "description": "  my reading list  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name")
                        .value("Reading"))
                .andExpect(jsonPath("$.data.description")
                        .value("my reading list"))
                .andExpect(jsonPath("$.message")
                        .value("集合创建成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-002"));

        ArgumentCaptor<CreateCollectionRequest> captor =
                ArgumentCaptor.forClass(
                        CreateCollectionRequest.class
                );

        verify(collectionService)
                .createCollection(captor.capture());

        assertThat(captor.getValue().name())
                .isEqualTo("Reading");
        assertThat(captor.getValue().description())
                .isEqualTo("my reading list");
    }

    // [测试意图] 验证创建重名集合返回 409 业务冲突。
    @Test
    void shouldReturnConflictWhenCollectionNameExists()
            throws Exception {
        when(collectionService.createCollection(
                any(CreateCollectionRequest.class)
        )).thenThrow(new BusinessException(
                ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
        ));

        mockMvc.perform(post("/api/v1/collections")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-003"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Reading",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COLLECTION_NAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("集合名称已存在"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-003"));
    }

    // [测试意图] 验证更新集合返回 200、更新成功消息和更新后的摘要。
    @Test
    void shouldUpdateCollectionWhenRequestIsValid() throws Exception {
        when(collectionService.updateCollection(
                eq(9L),
                any(UpdateCollectionRequest.class)
        )).thenReturn(summary(
                9L,
                "Updated",
                "updated description",
                false
        ));

        mockMvc.perform(patch("/api/v1/collections/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-004"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated",
                                  "description": "updated description"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name")
                        .value("Updated"))
                .andExpect(jsonPath("$.message")
                        .value("集合更新成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-004"));

        verify(collectionService).updateCollection(
                eq(9L),
                any(UpdateCollectionRequest.class)
        );
    }

    // [测试意图] 验证删除普通集合返回 200 和删除成功消息。
    @Test
    void shouldDeleteCollection() throws Exception {
        mockMvc.perform(delete("/api/v1/collections/9")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-005"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("集合删除成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-005"));

        verify(collectionService).deleteCollection(9L);
    }

    // [测试意图] 验证删除 Inbox 返回 409 且不会调用额外删除路径。
    @Test
    void shouldReturnConflictWhenDeletingInbox() throws Exception {
        doThrow(new BusinessException(
                ErrorCode.COLLECTION_INBOX_PROTECTED
        )).when(collectionService).deleteCollection(1L);

        mockMvc.perform(delete("/api/v1/collections/1")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-006"
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("COLLECTION_INBOX_PROTECTED"))
                .andExpect(jsonPath("$.message")
                        .value("默认 Inbox 不允许删除"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-006"));
    }

    // [测试意图] 验证未登录访问集合接口返回 401 AUTH_REQUIRED。
    @Test
    void shouldReturnUnauthorizedWhenNotLoggedIn() throws Exception {
        when(collectionService.listCollections())
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_REQUIRED
                ));

        mockMvc.perform(get("/api/v1/collections")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-007"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("请先登录"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-007"));
    }

    // [测试意图] 验证空集合名称返回 400 且不会调用 CollectionService。
    @Test
    void shouldReturnValidationErrorWhenNameIsBlank()
            throws Exception {
        mockMvc.perform(post("/api/v1/collections")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-collection-008"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "   ",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.name")
                        .value("集合名称不能为空"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-collection-008"));

        verifyNoInteractions(collectionService);
    }

    private static CollectionSummary summary(
            Long id,
            String name,
            String description,
            boolean isInbox
    ) {
        return new CollectionSummary(
                id,
                name,
                description,
                isInbox,
                0L,
                FIXED_INSTANT
        );
    }
}
