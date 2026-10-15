package com.webcollector.collection.service;

import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.collection.dto.CollectionSummary;
import com.webcollector.collection.dto.CreateCollectionRequest;
import com.webcollector.collection.dto.UpdateCollectionRequest;
import com.webcollector.collection.entity.LinkCollection;
import com.webcollector.collection.mapper.LinkCollectionMapper;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.link.mapper.LinkMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CollectionServiceTest {
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-10T12:00:00Z");
    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    private LinkCollectionMapper linkCollectionMapper;
    private LinkMapper linkMapper;
    private SessionTokenService sessionTokenService;
    private CollectionService collectionService;

    @BeforeEach
    void setUp() {
        linkCollectionMapper = mock(LinkCollectionMapper.class);
        linkMapper = mock(LinkMapper.class);
        sessionTokenService = mock(SessionTokenService.class);

        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        collectionService = new CollectionService(
                linkCollectionMapper,
                linkMapper,
                sessionTokenService,
                clock
        );
    }

    // [测试意图] 验证用户没有 Inbox 时会创建一个固定时间和固定名称的默认 Inbox。
    @Test
    void shouldCreateDefaultInboxWhenUserHasNone() {
        when(linkCollectionMapper.selectCount(any()))
                .thenReturn(0L);

        collectionService.ensureDefaultInbox(1L);

        ArgumentCaptor<LinkCollection> captor =
                ArgumentCaptor.forClass(LinkCollection.class);

        verify(linkCollectionMapper).insert(captor.capture());

        LinkCollection inbox = captor.getValue();

        assertThat(inbox.getUserId()).isEqualTo(1L);
        assertThat(inbox.getName()).isEqualTo("Inbox");
        assertThat(inbox.getDescription()).isEmpty();
        assertThat(inbox.getIsInbox()).isTrue();
        assertThat(inbox.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(inbox.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    // [测试意图] 验证用户已有 Inbox 时不会重复插入默认 Inbox。
    @Test
    void shouldNotCreateDefaultInboxWhenAlreadyExists() {
        when(linkCollectionMapper.selectCount(any()))
                .thenReturn(1L);

        collectionService.ensureDefaultInbox(1L);

        verify(linkCollectionMapper, never())
                .insert(any(LinkCollection.class));
    }

    // [测试意图] 验证更新不属于当前用户的集合时返回 NOT_FOUND。
    @Test
    void shouldRejectUpdateWhenCollectionDoesNotBelongToCurrentUser() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(null);

        assertThatThrownBy(
                () -> collectionService.updateCollection(
                        99L,
                        new UpdateCollectionRequest(
                                "New name",
                                "new description"
                        )
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND)
        );

        verify(linkCollectionMapper, never())
                .updateById(any(LinkCollection.class));
    }

    // [测试意图] 验证更新为当前用户已存在的集合名称时返回业务冲突。
    @Test
    void shouldRejectUpdateWhenNameAlreadyExists() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(collection(
                        30L,
                        1L,
                        "Old name",
                        false
                ));

        when(linkCollectionMapper.selectCount(any()))
                .thenReturn(1L);

        assertThatThrownBy(
                () -> collectionService.updateCollection(
                        30L,
                        new UpdateCollectionRequest(
                                "Reading",
                                "new description"
                        )
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
                        )
        );

        verify(linkCollectionMapper, never())
                .updateById(any(LinkCollection.class));
    }

    // [测试意图] 验证更新自己的集合并正确写入名称、描述和更新时间。
    @Test
    void shouldUpdateOwnedCollection() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        LinkCollection existing = collection(
                30L,
                1L,
                "Old name",
                false
        );

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(existing);

        when(linkCollectionMapper.selectCount(any()))
                .thenReturn(0L);

        CollectionSummary summary =
                collectionService.updateCollection(
                        30L,
                        new UpdateCollectionRequest(
                                "Reading",
                                "new description"
                        )
                );

        assertThat(summary.id()).isEqualTo(30L);
        assertThat(summary.name()).isEqualTo("Reading");
        assertThat(summary.description())
                .isEqualTo("new description");
        assertThat(existing.getUpdatedAt())
                .isEqualTo(FIXED_TIME);

        verify(linkCollectionMapper).updateById(existing);
    }

    // [测试意图] 验证默认 Inbox 不允许删除且不会执行数据库删除。
    @Test
    void shouldRejectDeletingInbox() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(collection(
                        40L,
                        1L,
                        "Inbox",
                        true
                ));

        assertThatThrownBy(
                () -> collectionService.deleteCollection(40L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.COLLECTION_INBOX_PROTECTED
                        )
        );

        verify(linkCollectionMapper, never())
                .deleteById(anyLong());
    }

    // [测试意图] 验证普通集合删除时执行 deleteById。
    @Test
    void shouldDeleteOwnedCollection() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(collection(
                        50L,
                        1L,
                        "Reading",
                        false
                ));

        collectionService.deleteCollection(50L);

        verify(linkCollectionMapper).deleteById(50L);
    }

    // [测试意图] 验证删除不存在或不属于当前用户的集合时返回 NOT_FOUND。
    @Test
    void shouldRejectDeleteWhenCollectionNotFound() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(null);

        assertThatThrownBy(
                () -> collectionService.deleteCollection(99L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND)
        );

        verify(linkCollectionMapper, never())
                .deleteById(anyLong());
    }

    // [测试意图] 验证列表只查询当前登录用户的集合并转换为安全摘要。
    @Test
    void shouldListOnlyCurrentUsersCollections() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        LinkCollection inbox = collection(
                10L,
                1L,
                "Inbox",
                true
        );

        LinkCollection reading = collection(
                11L,
                1L,
                "Reading",
                false
        );

        when(linkCollectionMapper.selectList(any()))
                .thenReturn(List.of(inbox, reading));

        // [软件工程] 显式声明计数为 0，不依赖 Mockito 对 Long 返回值默认给 null 的行为。
        when(linkMapper.selectCount(any()))
                .thenReturn(0L);

        List<CollectionSummary> result =
                collectionService.listCollections();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).name()).isEqualTo("Inbox");
        assertThat(result.get(0).isInbox()).isTrue();
        assertThat(result.get(0).linkCount()).isZero();
        assertThat(result.get(0).createdAt())
                .isEqualTo(FIXED_INSTANT);

        assertThat(result.get(1).id()).isEqualTo(11L);
        assertThat(result.get(1).name()).isEqualTo("Reading");
        assertThat(result.get(1).isInbox()).isFalse();

        verify(sessionTokenService).requireLoginId();
        verify(linkCollectionMapper).selectList(any());
    }

    // [测试意图] 验证创建成功时使用当前用户、规范化输入并返回集合摘要。
    @Test
    void shouldCreateCollectionForCurrentUser() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectCount(any()))
                .thenReturn(0L);

        when(linkCollectionMapper.insert(any(LinkCollection.class)))
                .thenAnswer(invocation -> {
                    LinkCollection collection =
                            invocation.getArgument(0);
                    collection.setId(20L);
                    return 1;
                });

        CollectionSummary summary =
                collectionService.createCollection(
                        new CreateCollectionRequest(
                                "  Reading  ",
                                "  my reading list  "
                        )
                );

        assertThat(summary.id()).isEqualTo(20L);
        assertThat(summary.name()).isEqualTo("Reading");
        assertThat(summary.description())
                .isEqualTo("my reading list");
        assertThat(summary.isInbox()).isFalse();
        assertThat(summary.linkCount()).isZero();
        assertThat(summary.createdAt())
                .isEqualTo(FIXED_INSTANT);

        ArgumentCaptor<LinkCollection> captor =
                ArgumentCaptor.forClass(LinkCollection.class);

        verify(linkCollectionMapper).insert(captor.capture());

        LinkCollection saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("Reading");
        assertThat(saved.getDescription())
                .isEqualTo("my reading list");
        assertThat(saved.getIsInbox()).isFalse();
        assertThat(saved.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(saved.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    // [测试意图] 验证创建重名集合时返回业务冲突且不执行插入。
    @Test
    void shouldRejectDuplicateCollectionNameOnCreate() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectCount(any()))
                .thenReturn(1L);

        assertThatThrownBy(
                () -> collectionService.createCollection(
                        new CreateCollectionRequest(
                                "Reading",
                                "my reading list"
                        )
                )
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
                        )
        );

        verify(linkCollectionMapper, never())
                .insert(any(LinkCollection.class));
    }

    // [测试意图] 验证集合里还有链接时拒绝删除，且不执行数据库删除。
    @Test
    void shouldRejectDeletingCollectionThatStillHasLinks() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(collection(
                        60L,
                        1L,
                        "Reading",
                        false
                ));

        when(linkMapper.selectCount(any()))
                .thenReturn(2L);

        assertThatThrownBy(
                () -> collectionService.deleteCollection(60L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.COLLECTION_NOT_EMPTY)
        );

        verify(linkCollectionMapper, never())
                .deleteById(anyLong());
    }

    // [测试意图] 验证"非空 Inbox"报的是 Inbox 保护规则而不是非空规则——判断顺序不能颠倒。
    @Test
    void shouldReportInboxProtectionBeforeLinkCount() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectOne(any()))
                .thenReturn(collection(
                        40L,
                        1L,
                        "Inbox",
                        true
                ));

        when(linkMapper.selectCount(any()))
                .thenReturn(5L);

        assertThatThrownBy(
                () -> collectionService.deleteCollection(40L)
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.COLLECTION_INBOX_PROTECTED
                        )
        );

        // [软件工程] Inbox 判断在查链接之前就短路了，所以计数查询根本不该发生；
        // 如果哪天顺序被写反，上面那条错误码断言会先失败。
        verify(linkMapper, never()).selectCount(any());
        verify(linkCollectionMapper, never())
                .deleteById(anyLong());
    }

    // [测试意图] 验证列表返回的是真实链接计数，而不是写死的 0。
    @Test
    void shouldReturnRealLinkCountWhenListing() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(linkCollectionMapper.selectList(any()))
                .thenReturn(List.of(collection(
                        11L,
                        1L,
                        "Reading",
                        false
                )));

        when(linkMapper.selectCount(any()))
                .thenReturn(3L);

        List<CollectionSummary> result =
                collectionService.listCollections();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).linkCount()).isEqualTo(3L);
    }

    private static LinkCollection collection(
            Long id,
            Long userId,
            String name,
            boolean isInbox
    ) {
        LinkCollection collection = new LinkCollection();
        collection.setId(id);
        collection.setUserId(userId);
        collection.setName(name);
        collection.setDescription("");
        collection.setIsInbox(isInbox);
        collection.setCreatedAt(FIXED_TIME);
        collection.setUpdatedAt(FIXED_TIME);
        return collection;
    }
}
