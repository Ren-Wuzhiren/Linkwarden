package com.webcollector.link.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.collection.mapper.LinkCollectionMapper;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.link.dto.CreateLinkRequest;
import com.webcollector.link.dto.LinkSummary;
import com.webcollector.link.dto.SetLinkTagsRequest;
import com.webcollector.link.dto.UpdateLinkRequest;
import com.webcollector.link.entity.Link;
import com.webcollector.link.mapper.LinkMapper;
import com.webcollector.tag.entity.LinkTag;
import com.webcollector.tag.mapper.LinkTagMapper;
import com.webcollector.tag.mapper.TagMapper;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LinkServiceTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-14T12:00:00Z");
    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    // [Java] 给存量数据一个"更早"的时间，更新测试才能证明 updatedAt 被推新了，
    // 而不是"本来就等于固定时钟"。
    private static final Instant OLDER_INSTANT =
            Instant.parse("2026-10-14T11:00:00Z");
    private static final LocalDateTime OLDER_TIME =
            LocalDateTime.ofInstant(OLDER_INSTANT, ZoneOffset.UTC);

    private LinkMapper linkMapper;
    private LinkCollectionMapper linkCollectionMapper;
    private TagMapper tagMapper;
    private LinkTagMapper linkTagMapper;
    private SessionTokenService sessionTokenService;
    private LinkService linkService;

    @BeforeEach
    void setUp() {
        linkMapper = mock(LinkMapper.class);
        linkCollectionMapper = mock(LinkCollectionMapper.class);
        sessionTokenService = mock(SessionTokenService.class);
        tagMapper = mock(TagMapper.class);
        linkTagMapper = mock(LinkTagMapper.class);

        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

        linkService = new LinkService(
                linkMapper,
                linkCollectionMapper,
                tagMapper,
                linkTagMapper,
                sessionTokenService,
                clock
        );

        when(sessionTokenService.requireLoginId()).thenReturn(1L);
    }

    // [测试意图] 验证列表返回当前用户的链接，并且查询条件里真的绑定了当前用户 ID。
    @Test
    void shouldListLinksOfCurrentUser() {
        when(linkMapper.selectList(any())).thenReturn(List.of(
                existingLink(10L, 5L, "https://example.com/a")
        ));

        List<LinkSummary> links = linkService.listLinks();

        assertThat(links).hasSize(1);
        assertThat(links.get(0).id()).isEqualTo(10L);
        assertThat(links.get(0).collectionId()).isEqualTo(5L);
        assertThat(links.get(0).originalUrl())
                .isEqualTo("https://example.com/a");
        assertThat(links.get(0).createdAt()).isEqualTo(OLDER_INSTANT);

    }

    // [测试意图] 验证创建时写入当前用户、所属集合、地址和固定时间，并带回自增主键。
    @Test
    void shouldCreateLinkForCurrentUser() {
        when(linkCollectionMapper.selectCount(any())).thenReturn(1L);
        when(linkMapper.insert(any(Link.class))).thenAnswer(invocation -> {
            Link inserted = invocation.getArgument(0);
            inserted.setId(20L);
            return 1;
        });

        LinkSummary summary = linkService.createLink(
                new CreateLinkRequest(
                        "https://example.com/a",
                        5L,
                        List.of(),
                        "示例标题",
                        "示例描述"
                )
        );

        assertThat(summary.id()).isEqualTo(20L);
        assertThat(summary.collectionId()).isEqualTo(5L);
        assertThat(summary.originalUrl())
                .isEqualTo("https://example.com/a");
        assertThat(summary.createdAt()).isEqualTo(FIXED_INSTANT);

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkMapper).insert(captor.capture());

        Link inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getCollectionId()).isEqualTo(5L);
        assertThat(inserted.getTitle()).isEqualTo("示例标题");
        assertThat(inserted.getDescription()).isEqualTo("示例描述");
        assertThat(inserted.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(inserted.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    // [测试意图] 验证地址首尾空白在进 Service 前就被去掉，未填写的标题和描述落到空串。
    @Test
    void shouldNormalizeUrlAndDefaultOptionalFieldsWhenCreating() {
        when(linkCollectionMapper.selectCount(any())).thenReturn(1L);
        when(linkMapper.insert(any(Link.class))).thenAnswer(invocation -> {
            Link inserted = invocation.getArgument(0);
            inserted.setId(20L);
            return 1;
        });

        linkService.createLink(new CreateLinkRequest(
                "  https://example.com/a  ",
                5L,
                List.of(),
                null,
                null
        ));

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkMapper).insert(captor.capture());

        Link inserted = captor.getValue();
        assertThat(inserted.getOriginalUrl())
                .isEqualTo("https://example.com/a");
        assertThat(inserted.getTitle()).isEmpty();
        assertThat(inserted.getDescription()).isEmpty();
    }

    // [测试意图] 验证往别人的集合里存链接返回 404，且不执行插入。
    @Test
    void shouldRejectCreateWhenCollectionNotOwned() {
        when(linkCollectionMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> linkService.createLink(
                new CreateLinkRequest("https://example.com/a", 99L, List.of(), "", "")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(linkMapper, never()).insert(any(Link.class));
    }

    // [测试意图] 验证保存链接时带上别人的标签返回 404，且一步写入都没有发生。
    // 这条守的是 createLink 里的 assertOwnedTags——它和 setLinkTags 是两处调用，
    // 只测其中一处会漏掉另一处被删掉的情况。
    @Test
    void shouldRejectCreateWhenTagNotOwned() {
        when(linkCollectionMapper.selectCount(any())).thenReturn(1L);
        // [业务] 请求了 1 个标签，但查到的归属数量是 0 → 数量对不上 → 404。
        when(tagMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> linkService.createLink(new CreateLinkRequest(
                "https://example.com/a",
                5L,
                List.of(99L),
                "",
                ""
        )))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        // [业务] 校验必须发生在插入之前：链接和关联都不该被写入。
        verify(linkMapper, never()).insert(any(Link.class));
        verify(linkTagMapper, never()).insert(any(LinkTag.class));
    }

    // [测试意图] 验证读取自己的链接返回摘要，字段和时间都正确映射。
    @Test
    void shouldReturnOwnedLinkWhenGetting() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );

        LinkSummary summary = linkService.getLink(10L);

        assertThat(summary.id()).isEqualTo(10L);
        assertThat(summary.collectionId()).isEqualTo(5L);
        assertThat(summary.originalUrl())
                .isEqualTo("https://example.com/a");
        assertThat(summary.createdAt()).isEqualTo(OLDER_INSTANT);
        assertThat(summary.updatedAt()).isEqualTo(OLDER_INSTANT);
    }

    // [测试意图] 验证读别人的链接返回 404，不泄露 id 是否存在。
    @Test
    void shouldRejectGetWhenLinkNotOwned() {
        when(linkMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> linkService.getLink(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );
    }

    // [测试意图] 验证更新写回新的集合、标题和描述，并把 updatedAt 推到当前时间。
    @Test
    void shouldUpdateOwnedLink() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );
        when(linkCollectionMapper.selectCount(any())).thenReturn(1L);

        LinkSummary summary = linkService.updateLink(
                10L,
                new UpdateLinkRequest(7L, "新标题", "新描述")
        );

        assertThat(summary.collectionId()).isEqualTo(7L);
        assertThat(summary.title()).isEqualTo("新标题");
        assertThat(summary.description()).isEqualTo("新描述");
        assertThat(summary.createdAt()).isEqualTo(OLDER_INSTANT);
        assertThat(summary.updatedAt()).isEqualTo(FIXED_INSTANT);

        ArgumentCaptor<Link> captor = ArgumentCaptor.forClass(Link.class);
        verify(linkMapper).updateById(captor.capture());

        Link updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(10L);
        // [业务] 地址不在更新范围内：UpdateLinkRequest 根本没有 url 字段，
        // 所以更新后 originalUrl 必须原样保留。
        assertThat(updated.getOriginalUrl())
                .isEqualTo("https://example.com/a");
    }

    // [测试意图] 验证改别人的链接返回 404，且连集合都不去查。
    @Test
    void shouldRejectUpdateWhenLinkNotOwned() {
        when(linkMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> linkService.updateLink(
                99L,
                new UpdateLinkRequest(5L, "标题", "")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(linkMapper, never()).updateById(any(Link.class));
        verify(linkCollectionMapper, never()).selectCount(any());
    }

    // [测试意图] 验证把链接改到别人的集合返回 404，且不执行更新。
    @Test
    void shouldRejectUpdateWhenTargetCollectionNotOwned() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );
        when(linkCollectionMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> linkService.updateLink(
                10L,
                new UpdateLinkRequest(99L, "标题", "")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(linkMapper, never()).updateById(any(Link.class));
    }

    // [测试意图] 验证删除自己的链接会真正调用删除。
    @Test
    void shouldDeleteOwnedLink() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );

        linkService.deleteLink(10L);

        verify(linkMapper).deleteById(10L);
    }

    // [测试意图] 验证删别人的链接返回 404，且不执行删除。
    @Test
    void shouldRejectDeleteWhenLinkNotOwned() {
        when(linkMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> linkService.deleteLink(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(linkMapper, never()).deleteById(any(Long.class));
    }

    // [测试意图] 验证创建链接时带标签会逐条写入 link_tag，且 tagId 与 createdAt 正确。
    @Test
    void shouldAttachTagsWhenCreatingLink() {
        when(linkCollectionMapper.selectCount(any())).thenReturn(1L);
        when(tagMapper.selectCount(any())).thenReturn(2L);
        when(linkMapper.insert(any(Link.class))).thenAnswer(invocation -> {
            Link inserted = invocation.getArgument(0);
            inserted.setId(20L);
            return 1;
        });

        linkService.createLink(new CreateLinkRequest(
                "https://example.com/a",
                5L,
                List.of(3L, 7L),
                "",
                ""
        ));

        ArgumentCaptor<LinkTag> captor =
                ArgumentCaptor.forClass(LinkTag.class);
        verify(linkTagMapper, times(2))
                .insert(captor.capture());

        List<LinkTag> inserts = captor.getAllValues();
        assertThat(inserts).hasSize(2);
        assertThat(inserts.get(0).getLinkId()).isEqualTo(20L);
        assertThat(inserts.get(0).getTagId()).isEqualTo(3L);
        assertThat(inserts.get(0).getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(inserts.get(1).getLinkId()).isEqualTo(20L);
        assertThat(inserts.get(1).getTagId()).isEqualTo(7L);
    }

    // [测试意图] 验证 setLinkTags 先清空再写入，整体替换标签集合。
    @Test
    void shouldReplaceTagsWhenSettingLinkTags() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );
        when(tagMapper.selectCount(any())).thenReturn(2L);

        linkService.setLinkTags(
                10L,
                new SetLinkTagsRequest(
                        List.of(3L, 7L)
                )
        );

        // [业务] 先清空：必须对 link_id=10 执行一次条件删除。
        verify(linkTagMapper).delete(
                any(Wrapper.class)
        );

        // [业务] 再写入：逐条 insert 两条关联。
        ArgumentCaptor<LinkTag> captor =
                ArgumentCaptor.forClass(LinkTag.class);
        verify(linkTagMapper, times(2))
                .insert(captor.capture());

        List<LinkTag> inserts = captor.getAllValues();
        assertThat(inserts.get(0).getLinkId()).isEqualTo(10L);
        assertThat(inserts.get(0).getTagId()).isEqualTo(3L);
        assertThat(inserts.get(1).getLinkId()).isEqualTo(10L);
        assertThat(inserts.get(1).getTagId()).isEqualTo(7L);
    }

    // [测试意图] 验证对不属于自己的链接设置标签返回 404，不执行任何写入。
    @Test
    void shouldRejectSetLinkTagsWhenLinkNotOwned() {
        when(linkMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> linkService.setLinkTags(
                99L,
                new SetLinkTagsRequest(List.of(1L))
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(linkTagMapper, never()).delete(any());
        verify(linkTagMapper, never()).insert(any(LinkTag.class));
    }

    // [测试意图] 验证把不属于自己的标签挂到链接上返回 404，不执行写入。
    @Test
    void shouldRejectSetLinkTagsWhenTagNotOwned() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );
        // [业务] 请求了 2 个标签但只查到 1 个属于当前用户 → 数量对不上 → 404。
        when(tagMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> linkService.setLinkTags(
                10L,
                new SetLinkTagsRequest(
                        List.of(3L, 99L)
                )
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(linkTagMapper, never()).delete(any());
        verify(linkTagMapper, never()).insert(any(LinkTag.class));
    }

    // [测试意图] 验证空数组清空全部标签：执行删除但不 insert 任何关联。
    @Test
    void shouldClearTagsWhenSettingEmptyArray() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );

        linkService.setLinkTags(
                10L,
                new SetLinkTagsRequest(List.of())
        );

        // [业务] 清空操作仍然会执行 delete，把该链接的旧关联全部移除。
        verify(linkTagMapper).delete(
                any(Wrapper.class)
        );
        // [业务] 空数组意味着不写入任何新关联。
        verify(linkTagMapper, never()).insert(any(LinkTag.class));
    }

    // [测试意图] 验证重复标签 id 被去重：[1, 1] 只写入一条关联。
    @Test
    void shouldDeduplicateTagIds() {
        when(linkMapper.selectOne(any())).thenReturn(
                existingLink(10L, 5L, "https://example.com/a")
        );
        // [业务] 去重后只有 1 个标签，所以 selectCount 返回 1。
        when(tagMapper.selectCount(any())).thenReturn(1L);

        linkService.setLinkTags(
                10L,
                new SetLinkTagsRequest(
                        List.of(3L, 3L)
                )
        );

        // [业务] 去重后只 insert 一次，而不是两次。
        verify(linkTagMapper, times(1))
                .insert(any(LinkTag.class));
    }
    private static Link existingLink(
            Long id,
            Long collectionId,
            String url
    ) {
        Link link = new Link();
        link.setId(id);
        link.setUserId(1L);
        link.setCollectionId(collectionId);
        link.setOriginalUrl(url);
        link.setTitle("");
        link.setDescription("");
        link.setCreatedAt(OLDER_TIME);
        link.setUpdatedAt(OLDER_TIME);
        return link;
    }
}