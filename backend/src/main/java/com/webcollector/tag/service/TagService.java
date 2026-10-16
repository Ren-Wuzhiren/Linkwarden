package com.webcollector.tag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.tag.dto.CreateTagRequest;
import com.webcollector.tag.dto.TagSummary;
import com.webcollector.tag.dto.UpdateTagRequest;
import com.webcollector.tag.entity.LinkTag;
import com.webcollector.tag.entity.Tag;
import com.webcollector.tag.mapper.LinkTagMapper;
import com.webcollector.tag.mapper.TagMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TagService {
    private final TagMapper tagMapper;
    private final LinkTagMapper linkTagMapper;
    private final SessionTokenService sessionTokenService;
    private final Clock clock;

    public TagService(
            TagMapper tagMapper,
            LinkTagMapper linkTagMapper,
            SessionTokenService sessionTokenService,
            Clock clock
    ) {
        this.tagMapper = tagMapper;
        this.linkTagMapper = linkTagMapper;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
    }

    /**
     * [业务] 只返回当前用户自己的标签，并带上每个标签的真实被用次数。
     *
     * [软件工程] 这里是一个已知的 N+1：标签有几个，就多查几次关联计数。
     * 与 CollectionService.listCollections 的处理方式一致——个人归档的标签数量是
     * 个位数，几次走索引的 COUNT 远便宜于引入一条自定义分组 SQL 的复杂度。
     *
     * [408] 优化方向很明确：用 GROUP BY tag_id 一次查完全部计数，或者把被用次数
     * 做成 tag 表上的冗余列随写入维护。等 M6.2 做分页时一起评估。
     */
    @Transactional(readOnly = true)
    public List<TagSummary> listTags() {
        Long userId = currentUserId();

        return tagMapper.selectList(
                        new LambdaQueryWrapper<Tag>()
                                .eq(Tag::getUserId, userId)
                                .orderByAsc(Tag::getId)
                )
                .stream()
                .map(tag -> toSummary(tag, countLinks(tag.getId())))
                .toList();
    }

    @Transactional
    public TagSummary createTag(CreateTagRequest request) {
        Long userId = currentUserId();

        // [业务] 先查一次是为了给出友好错误；真正的唯一性由数据库索引兜底。
        if (existsByNameExcept(userId, request.name(), null)) {
            throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
        }

        LocalDateTime now = now();
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(request.name());
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);

        try {
            tagMapper.insert(tag);
        } catch (DuplicateKeyException exception) {
            // [业务] 两个请求同时通过了上面的查询时，只会有一个 insert 成功，
            // 另一个走到这里，翻译成同一个 409，不把数据库异常原样暴露给客户端。
            throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
        }

        // [业务] 刚建出来的标签必然还没被任何链接使用，不需要为它多查一次计数。
        return toSummary(tag, 0L);
    }

    @Transactional
    public TagSummary updateTag(Long tagId, UpdateTagRequest request) {
        Long userId = currentUserId();
        Tag tag = findOwned(tagId, userId);

        // [业务] 别人的标签一律当作不存在，返回 404 而不是 403，避免泄露 id 是否存在。
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (existsByNameExcept(userId, request.name(), tagId)) {
            throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
        }

        tag.setName(request.name());
        tag.setUpdatedAt(now());

        try {
            tagMapper.updateById(tag);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.TAG_NAME_ALREADY_EXISTS);
        }

        return toSummary(tag, countLinks(tagId));
    }

    @Transactional
    public void deleteTag(Long tagId) {
        Long userId = currentUserId();
        Tag tag = findOwned(tagId, userId);

        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // [业务] 这里刻意不手工删除 link_tag 里的关联：link_tag.tag_id 的外键是
        // ON DELETE CASCADE，数据库会把关联清掉，而链接本身完好无损。
        // 这正是 06-接口设计 承诺的"删除标签及链接关联，不删除链接"。
        // [软件工程] 契约由数据库持有，服务层不重复表达同一条规则；
        // 由 LinkTagMapperTest 的级联测试钉住它。
        tagMapper.deleteById(tagId);
    }

    private Long currentUserId() {
        return sessionTokenService.requireLoginId();
    }

    private Tag findOwned(Long tagId, Long userId) {
        return tagMapper.selectOne(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getId, tagId)
                        .eq(Tag::getUserId, userId)
        );
    }

    /**
     * [业务] 判断某用户下是否存在同名标签，可排除指定 id（改名时排除自己）。
     *
     * [408] 这里不做小写化，因为比较语义由数据库列的排序规则决定：
     * 表用的是 utf8mb4_0900_ai_ci（ci = case insensitive），
     * 所以 WHERE name = 'Java' 会命中 'java'。排除自身后，改大小写就不再被误判为冲突。
     *
     * [排错] 不要在 Java 侧加 toLowerCase() 或 equalsIgnoreCase 来"补"这个判断——
     * 那等于用另一套规则重算一遍，查重说"没有"、插入却报 409，两套规则迟早会分叉。
     */
    private boolean existsByNameExcept(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<Tag> query = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, userId)
                .eq(Tag::getName, name);

        // [排错] excludeId 为 null 时不能调用 ne：MyBatis-Plus 会拼出 `id <> NULL`，
        // 该条件在 SQL 里恒为 unknown，查重会永远返回 0。
        if (excludeId != null) {
            query.ne(Tag::getId, excludeId);
        }

        Long count = tagMapper.selectCount(query);

        return count != null && count > 0;
    }

    /**
     * [408] 统计某个标签被多少条链接使用。条件是 tag_id，用的正是 InnoDB 为
     * fk_link_tag_tag 自动建的那个索引（联合主键的最左列是 link_id，用不上它）。
     *
     * [业务] 这里不需要再带 user_id：tag_id 已经唯一确定了一个用户，而关联写入时
     * LinkService.assertOwnedTags 已强制"链接与标签同属一人"。这条不变量是
     * link_tag 敢于不带 user_id 列的前提。
     */
    private long countLinks(Long tagId) {
        Long count = linkTagMapper.selectCount(
                new LambdaQueryWrapper<LinkTag>()
                        .eq(LinkTag::getTagId, tagId)
        );

        return count == null ? 0L : count;
    }

    // [Java] 时间通过注入的 Clock 生成，而不是直接 LocalDateTime.now()，
    // 这样测试可以注入固定时钟，精确断言 createdAt / updatedAt。
    // [408] 截断到毫秒是为了与 MySQL DATETIME(3) 的精度对齐。
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private TagSummary toSummary(Tag tag, long linkCount) {
        return new TagSummary(
                tag.getId(),
                tag.getName(),
                linkCount,
                // [Java] 数据库存的是不带时区的 LocalDateTime，这里按 UTC 语义转成 Instant 再对外输出。
                tag.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

}
