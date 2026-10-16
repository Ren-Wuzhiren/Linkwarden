package com.webcollector.link.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.collection.entity.LinkCollection;
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
import com.webcollector.tag.entity.Tag;
import com.webcollector.tag.mapper.LinkTagMapper;
import com.webcollector.tag.mapper.TagMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class LinkService {
    private final LinkMapper linkMapper;
    private final LinkCollectionMapper linkCollectionMapper;
    private final TagMapper tagMapper;
    private final LinkTagMapper linkTagMapper;
    private final SessionTokenService sessionTokenService;
    private final Clock clock;

    public LinkService(
            LinkMapper linkMapper,
            LinkCollectionMapper linkCollectionMapper,
            TagMapper tagMapper,
            LinkTagMapper linkTagMapper,
            SessionTokenService sessionTokenService,
            Clock clock
    ) {
        this.linkMapper = linkMapper;
        this.linkCollectionMapper = linkCollectionMapper;
        this.tagMapper = tagMapper;
        this.linkTagMapper = linkTagMapper;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
    }

    // [业务] 只返回当前用户自己的链接，查询条件里必须带 user_id。
    @Transactional(readOnly = true)
    public List<LinkSummary> listLinks() {
        Long userId = currentUserId();

        return linkMapper.selectList(
                        new LambdaQueryWrapper<Link>()
                                .eq(Link::getUserId, userId)
                                // [业务] 最新存的最想先看到，所以按 id 倒序；
                                // id 自增，倒序等价于"按创建时间从新到旧"。
                                .orderByDesc(Link::getId)
                )
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public LinkSummary createLink(CreateLinkRequest request) {
        Long userId = currentUserId();

        // [业务] collectionId 是客户端传进来的，必须确认它属于当前用户，
        // 否则等于允许把链接塞进别人的集合。
        assertOwnedCollection(request.collectionId(), userId);

        // [业务] 标签同理：一次带上若干个标签是允许的，但每个 id 都必须是自己的。
        List<Long> tagIds = distinctTagIds(request.tagIds());
        assertOwnedTags(tagIds, userId);

        LocalDateTime now = now();
        Link link = new Link();
        link.setUserId(userId);

        link.setCollectionId(request.collectionId());
        link.setOriginalUrl(request.url());
        link.setTitle(request.title());
        link.setDescription(request.description());
        link.setCreatedAt(now);
        link.setUpdatedAt(now);

        // [业务] 这一轮不接 DuplicateKeyException：link 表还没有任何唯一约束，
        // 重复地址的 409 翻译等 M2.5 加了 uk_link_user_url 再补。
        linkMapper.insert(link);

        // [业务] 关联写在链接插入之后、同一个事务里：要么链接和标签一起成功，
        // 要么一起回滚，不会留下"链接存了但标签丢了"的半截状态。
        attachTags(link.getId(), tagIds, now);

        return toSummary(link);
    }

    @Transactional(readOnly = true)
    public LinkSummary getLink(Long linkId) {
        Long userId = currentUserId();

        return toSummary(requireOwnedLink(linkId, userId));
    }

    @Transactional
    public LinkSummary updateLink(Long linkId, UpdateLinkRequest request) {
        Long userId = currentUserId();
        Link link = requireOwnedLink(linkId, userId);

        // [业务] 不管有没有换集合都校验一次：少一个分支，就少一条可能走错的路。
        assertOwnedCollection(request.collectionId(), userId);

        link.setCollectionId(request.collectionId());
        link.setTitle(request.title());
        link.setDescription(request.description());
        link.setUpdatedAt(now());

        linkMapper.updateById(link);

        return toSummary(link);
    }

    @Transactional
    public void deleteLink(Long linkId) {
        Long userId = currentUserId();

        // [业务] 先按"id + user_id"查出这一条，查到就说明它确实归我，然后再删。
        // [软件工程] 也可以把所有权条件直接拼进 DELETE 一条语句完成，这里保持和
        // TagService、CollectionService 一致的两步写法，便于对照阅读。
        Link link = requireOwnedLink(linkId, userId);

        // [业务] 链接身上的标签关联不用手工清理：link_tag.link_id 的外键是
        // ON DELETE CASCADE，数据库会跟着删。
        linkMapper.deleteById(link.getId());
    }

    /**
     * [业务] 用请求里的标签集合整体替换这条链接的标签。
     *
     * [软件工程] 这个方法放在 LinkService 而不是 TagService：入口是 /links/{id}/tags，
     * 而"这条链接归我"是第一道校验，和本类其他方法共用同一套所有权规则。
     */
    @Transactional
    public void setLinkTags(Long linkId, SetLinkTagsRequest request) {
        Long userId = currentUserId();

        // [业务] 先确认链接归我，别人的链接一律当作不存在。
        Link link = requireOwnedLink(linkId, userId);

        // [业务] 再确认这批标签全部归我。两道校验都通过之后才动数据，
        // 避免"已经删掉旧关联，才发现新标签不合法"。
        List<Long> tagIds = distinctTagIds(request.tagIds());
        assertOwnedTags(tagIds, userId);

        LocalDateTime now = now();

        // [业务] "替换"是先清空再写入，不是追加。空数组因此天然表示"清空全部标签"。
        // [软件工程] 这里用 Wrapper 而不是 deleteById：LinkTag 是联合主键表，
        // deleteById 会以 link_id 作为唯一标识匹配，语义上会跑偏。

        linkTagMapper.delete(
                new LambdaQueryWrapper<LinkTag>()
                        .eq(LinkTag::getLinkId, link.getId())
        );

        attachTags(link.getId(), tagIds, now);
    }


    private Long currentUserId() {
        return sessionTokenService.requireLoginId();
    }

    /**
     * [业务] 确认集合存在且属于当前用户，不属于就当作不存在。
     *
     * [408] 判断条件是 id 与 user_id 的联合过滤，而不是"先按 id 查出来再比 user_id"：
     * 一条 SQL 同时回答"存在吗"和"是我的吗"，也避免了两次查询之间的竞态。
     *
     * [软件工程] 方法名用 assert 而不是 find，因为调用方只需要"通过或抛异常"这个结果，
     * 不需要集合实体；名字把这一点写清楚，读者就不会去猜返回值。
     */
    private void assertOwnedCollection(Long collectionId, Long userId) {
        Long count = linkCollectionMapper.selectCount(
                new LambdaQueryWrapper<LinkCollection>()
                        .eq(LinkCollection::getId, collectionId)
                        .eq(LinkCollection::getUserId, userId)
        );

        if (count == null || count == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * [业务] 确认这些标签全部属于当前用户，有一个不属于就当作不存在。
     *
     * [408] 用一次 IN + COUNT 判断"数量对不对"，而不是逐个查询：
     * 只要查到的条数等于请求的条数，就说明这批 id 全部归我。
     * 空集合直接放行，省掉一次无意义的往返。
     *
     * [业务] 数量对不上时统一返回 404，不区分"不存在"和"不是你的"，
     * 避免通过状态码差异枚举出哪些 id 真实存在。
     */
    private void assertOwnedTags(List<Long> tagIds, Long userId) {
        if (tagIds.isEmpty()) {
            return;
        }

        Long count = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, userId)
                        .in(Tag::getId, tagIds)
        );

        // [Java] 显式取 longValue 再和 int 比，不依赖自动拆箱：
        // Long 与 int 直接比较会触发拆箱，读代码的人容易看错类型。
        if (count == null || count.longValue() != tagIds.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    /**
     * [业务] 标签 id 去重。
     *
     * [408] 请求体里出现 [1, 1] 时，link_tag 的联合主键会直接报重复键，
     * 所以去重必须发生在写入之前。
     *
     * [软件工程] 这套规则放在服务层而不是两个 DTO 的紧凑构造器里，是为了让
     * CreateLinkRequest 与 SetLinkTagsRequest 共用一份实现；而 Bean Validation
     * 在请求进入控制器时就跑完了，这里可以安全地假设集合里没有 null 元素。
     * [Java] 用 LinkedHashSet 而不是 HashSet：保持客户端给的顺序，便于调试与断言。
     */

    private List<Long> distinctTagIds(List<Long> tagIds) {
        return List.copyOf(new LinkedHashSet<>(tagIds));
    }

    // [软件工程] 标签数量是个位数，逐条 insert 比拼一条多值 INSERT 更好读，
    // 也更容易在出错时定位是哪一条。等出现批量导入场景再考虑批量写入。
    private void attachTags(
            Long linkId,
            List<Long> tagIds,
            LocalDateTime now
    ) {
        for (Long tagId : tagIds) {
            LinkTag linkTag = new LinkTag();
            linkTag.setLinkId(linkId);
            linkTag.setTagId(tagId);
            linkTag.setCreatedAt(now);
            linkTagMapper.insert(linkTag);
        }
    }

    // [业务] 别人的链接一律当作不存在，返回 404 而不是 403，避免泄露 id 是否存在。
    private Link requireOwnedLink(Long linkId, Long userId) {
        Link link = linkMapper.selectOne(
                new LambdaQueryWrapper<Link>()
                        .eq(Link::getId, linkId)
                        .eq(Link::getUserId, userId)
        );

        if (link == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        return link;
    }

    // [Java] 时间通过注入的 Clock 生成，而不是直接 LocalDateTime.now()，
    // 这样测试可以注入固定时钟，精确断言 createdAt / updatedAt。
    // [408] 截断到毫秒是为了与 MySQL DATETIME(3) 的精度对齐。
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private LinkSummary toSummary(Link link) {
        return new LinkSummary(
                link.getId(),
                link.getCollectionId(),
                link.getOriginalUrl(),
                link.getTitle(),
                link.getDescription(),
                // [Java] 数据库存的是不带时区的 LocalDateTime，这里按 UTC 语义转成 Instant 再对外输出。
                link.getCreatedAt().toInstant(ZoneOffset.UTC),
                link.getUpdatedAt().toInstant(ZoneOffset.UTC)
        );
    }
}