package com.webcollector.tag.service;

import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.tag.dto.CreateTagRequest;
import com.webcollector.tag.dto.TagSummary;
import com.webcollector.tag.dto.UpdateTagRequest;
import com.webcollector.tag.entity.Tag;
import com.webcollector.tag.mapper.TagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TagServiceTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-11T12:00:00Z");
    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    private TagMapper tagMapper;
    private SessionTokenService sessionTokenService;
    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        sessionTokenService = mock(SessionTokenService.class);

        Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

        tagService = new TagService(
                tagMapper,
                sessionTokenService,
                clock
        );

        when(sessionTokenService.requireLoginId()).thenReturn(1L);
    }

    // [测试意图] 验证列表返回当前用户的标签，linkCount 暂为 0，创建时间转成 Instant。
    @Test
    void shouldListTagsOfCurrentUser() {
        when(tagMapper.selectList(any()))
                .thenReturn(List.of(existingTag(10L, "Java")));

        List<TagSummary> tags = tagService.listTags();

        assertThat(tags).hasSize(1);
        assertThat(tags.get(0).id()).isEqualTo(10L);
        assertThat(tags.get(0).name()).isEqualTo("Java");
        assertThat(tags.get(0).linkCount()).isZero();
        assertThat(tags.get(0).createdAt()).isEqualTo(FIXED_INSTANT);
    }

    // [测试意图] 验证创建时写入当前用户 ID 与固定时间，并把回写的主键带进摘要。
    @Test
    void shouldCreateTagForCurrentUser() {
        when(tagMapper.selectCount(any())).thenReturn(0L);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
            Tag inserted = invocation.getArgument(0);
            inserted.setId(20L);
            return 1;
        });

        TagSummary summary = tagService.createTag(
                new CreateTagRequest("Java")
        );

        assertThat(summary.id()).isEqualTo(20L);
        assertThat(summary.name()).isEqualTo("Java");
        assertThat(summary.createdAt()).isEqualTo(FIXED_INSTANT);

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).insert(captor.capture());

        Tag inserted = captor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(1L);
        assertThat(inserted.getCreatedAt()).isEqualTo(FIXED_TIME);
        assertThat(inserted.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    // [测试意图] 验证查重命中时直接抛 409，且不执行插入。
    @Test
    void shouldRejectDuplicateNameWhenCreating() {
        when(tagMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> tagService.createTag(
                new CreateTagRequest("Java")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS)
                );

        verify(tagMapper, never()).insert(any(Tag.class));
    }

    // [测试意图] 验证并发下唯一键冲突被翻译成同一个 409，而不是泄漏数据库异常。
    @Test
    void shouldTranslateUniqueConstraintWhenCreating() {
        when(tagMapper.selectCount(any())).thenReturn(0L);
        when(tagMapper.insert(any(Tag.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(() -> tagService.createTag(
                new CreateTagRequest("Java")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS)
                );
    }

    // [测试意图] 验证改名会写回新名称与新的 updatedAt。
    @Test
    void shouldUpdateOwnedTag() {
        when(tagMapper.selectOne(any())).thenReturn(existingTag(10L, "Java"));
        when(tagMapper.selectCount(any())).thenReturn(0L);

        TagSummary summary = tagService.updateTag(
                10L,
                new UpdateTagRequest("JavaEE")
        );

        assertThat(summary.name()).isEqualTo("JavaEE");

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagMapper).updateById(captor.capture());

        Tag updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(10L);
        assertThat(updated.getName()).isEqualTo("JavaEE");
        assertThat(updated.getUpdatedAt()).isEqualTo(FIXED_TIME);
    }

    // [测试意图] 验证改别人的标签返回 404，且不执行更新（不泄露 id 是否存在）。
    @Test
    void shouldRejectUpdateWhenTagNotOwned() {
        when(tagMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> tagService.updateTag(
                99L,
                new UpdateTagRequest("Java")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(tagMapper, never()).updateById(any(Tag.class));
    }

    // [测试意图] 验证改成别的标签已占用的名字时返回 409。
    @Test
    void shouldRejectUpdateWhenNameTakenByAnotherTag() {
        when(tagMapper.selectOne(any())).thenReturn(existingTag(10L, "Java"));
        when(tagMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> tagService.updateTag(
                10L,
                new UpdateTagRequest("Kotlin")
        ))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.TAG_NAME_ALREADY_EXISTS)
                );

        verify(tagMapper, never()).updateById(any(Tag.class));
    }

    // [测试意图] 验证只改大小写不算冲突——查重排除了自身，这条曾是真缺陷。
    // 它真正被证明的地方在 TagMapperTest：只有真实 MySQL 的 ai_ci 排序规则
    // 才会让 "java" 命中原行，Mock 无法复现这一点。
    @Test
    void shouldAllowRenameToSameTagWithDifferentCase() {
        when(tagMapper.selectOne(any())).thenReturn(existingTag(10L, "Java"));
        when(tagMapper.selectCount(any())).thenReturn(0L);

        TagSummary summary = tagService.updateTag(
                10L,
                new UpdateTagRequest("java")
        );

        assertThat(summary.name()).isEqualTo("java");
        verify(tagMapper).updateById(any(Tag.class));
    }

    // [测试意图] 验证删除自己的标签会真正调用删除。
    @Test
    void shouldDeleteOwnedTag() {
        when(tagMapper.selectOne(any())).thenReturn(existingTag(10L, "Java"));

        tagService.deleteTag(10L);

        verify(tagMapper).deleteById(10L);
    }

    // [测试意图] 验证删别人的标签返回 404，且不执行删除。
    @Test
    void shouldRejectDeleteWhenTagNotOwned() {
        when(tagMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> tagService.deleteTag(99L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.NOT_FOUND)
                );

        verify(tagMapper, never()).deleteById(any(Long.class));
    }

    private static Tag existingTag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setUserId(1L);
        tag.setName(name);
        tag.setCreatedAt(FIXED_TIME);
        tag.setUpdatedAt(FIXED_TIME);
        return tag;
    }
}