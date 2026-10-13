package com.webcollector.tag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.tag.dto.CreateTagRequest;
import com.webcollector.tag.dto.TagSummary;
import com.webcollector.tag.dto.UpdateTagRequest;
import com.webcollector.tag.entity.Tag;
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
    private final SessionTokenService sessionTokenService;
    private final Clock clock;

    public TagService(
            TagMapper tagMapper,
            SessionTokenService sessionTokenService,
            Clock clock
    ) {
        this.tagMapper = tagMapper;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
    }

    // [业务] 只返回当前用户自己的标签，查询条件里必须带 user_id。
    @Transactional(readOnly = true)
    public List<TagSummary> listTags() {
        Long userId = currentUserId();

        return tagMapper.selectList(
                        new LambdaQueryWrapper<Tag>()
                                .eq(Tag::getUserId, userId)
                                .orderByAsc(Tag::getId)
                )
                .stream()
                .map(this::toSummary)
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

        return toSummary(tag);
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

        return toSummary(tag);
    }

    @Transactional
    public void deleteTag(Long tagId) {
        Long userId = currentUserId();
        Tag tag = findOwned(tagId, userId);

        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

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
     * [业务] 判断某用户下同名标签是否已存在。
     *
     * [408] 这里不做小写化，因为比较语义由数据库列的排序规则决定：
     * 表用的是 utf8mb4_0900_ai_ci（ci = case insensitive），
     * 所以 WHERE name = 'Java' 会命中 'java'。
     *
     * [排错] 如果这里自己加一层 toLowerCase()，业务层的判断就与数据库不一致了——
     * 查重说"没有"，插入却报 409，两边要一致才有意义。
     */
    /**
     * [业务] 判断某用户下是否存在同名标签，可排除指定 id（改名时排除自己）。
     *
     * [408] 比较语义由数据库列的排序规则决定：表用 utf8mb4_0900_ai_ci，
     * 所以 name = 'java' 会命中 'Java'。排除自身后，改大小写就不再被误判为冲突。
     *
     * [排错] 不要在 Java 侧加 equalsIgnoreCase 来"补"这个判断——
     * 那等于用另一套规则重算一遍，两套规则迟早会分叉。
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

    // [Java] 时间通过注入的 Clock 生成，而不是直接 LocalDateTime.now()，
    // 这样测试可以注入固定时钟，精确断言 createdAt / updatedAt。
    // [408] 截断到毫秒是为了与 MySQL DATETIME(3) 的精度对齐。
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private TagSummary toSummary(Tag tag) {
        return new TagSummary(
                tag.getId(),
                tag.getName(),
                0L,
                // [Java] 数据库存的是不带时区的 LocalDateTime，这里按 UTC 语义转成 Instant 再对外输出。
                tag.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

}
