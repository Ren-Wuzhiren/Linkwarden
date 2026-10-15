package com.webcollector.collection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.collection.dto.CollectionSummary;
import com.webcollector.collection.dto.CreateCollectionRequest;
import com.webcollector.collection.dto.UpdateCollectionRequest;
import com.webcollector.collection.entity.LinkCollection;
import com.webcollector.collection.mapper.LinkCollectionMapper;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.link.entity.Link;
import com.webcollector.link.mapper.LinkMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CollectionService {

    private static final String DEFAULT_INBOX_NAME = "Inbox";

    private final LinkCollectionMapper linkCollectionMapper;
    private final LinkMapper linkMapper;
    private final SessionTokenService sessionTokenService;
    private final Clock clock;

    public CollectionService(
            LinkCollectionMapper linkCollectionMapper,
            LinkMapper linkMapper,
            SessionTokenService sessionTokenService,
            Clock clock
    ) {
        this.linkCollectionMapper = linkCollectionMapper;
        this.linkMapper = linkMapper;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
    }

    // [业务] 注册成功后保证用户恰好拥有一个默认 Inbox。
    @Transactional
    public void ensureDefaultInbox(Long userId) {
        Long count = linkCollectionMapper.selectCount(
                new LambdaQueryWrapper<LinkCollection>()
                        .eq(LinkCollection::getUserId, userId)
                        .eq(LinkCollection::getIsInbox, true)
        );

        if (count != null && count > 0) {
            return;
        }

        LocalDateTime now = now();
        LinkCollection inbox = new LinkCollection();
        inbox.setUserId(userId);
        inbox.setName(DEFAULT_INBOX_NAME);
        inbox.setDescription("");
        inbox.setIsInbox(true);
        inbox.setCreatedAt(now);
        inbox.setUpdatedAt(now);

        linkCollectionMapper.insert(inbox);
    }

    /**
     * [业务] 列出当前用户的全部集合，并带上每个集合的真实链接数量。
     *
     * [软件工程] 这里是一个已知的 N+1：集合有几个，就多查几次链接计数。
     * 第一版先选正确性——个人归档的集合数量是个位数，几次走索引的 COUNT
     * 远便宜于引入一条自定义分组 SQL 的复杂度。
     *
     * [408] 优化方向很明确：用 GROUP BY collection_id 一次查完全部计数，
     * 或者把链接数量做成集合表上的冗余列随写入维护。等 M6.2 做分页时一起评估。
     */
    @Transactional(readOnly = true)
    public List<CollectionSummary> listCollections() {
        Long userId = currentUserId();

        return linkCollectionMapper.selectList(
                        new LambdaQueryWrapper<LinkCollection>()
                                .eq(LinkCollection::getUserId, userId)
                                .orderByAsc(LinkCollection::getId)
                )
                .stream()
                .map(collection -> toSummary(
                        collection,
                        countLinks(collection.getId(), userId)
                ))
                .toList();
    }

    @Transactional
    public CollectionSummary createCollection(
            CreateCollectionRequest request
    ) {
        Long userId = currentUserId();

        if (existsByName(userId, request.name())) {
            throw new BusinessException(
                    ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
            );
        }

        LocalDateTime now = now();
        LinkCollection collection = new LinkCollection();
        collection.setUserId(userId);
        collection.setName(request.name());
        collection.setDescription(request.description());
        collection.setIsInbox(false);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);

        try {
            linkCollectionMapper.insert(collection);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
            );
        }

        // [业务] 刚建出来的集合必然还没有链接，不需要为它多查一次计数。
        return toSummary(collection, 0L);
    }

    @Transactional
    public CollectionSummary updateCollection(
            Long collectionId,
            UpdateCollectionRequest request
    ) {
        Long userId = currentUserId();
        LinkCollection collection = findOwned(collectionId, userId);

        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // [排错] 这里还留着一个大小写缺陷：String.equals 区分大小写，而列是
        // utf8mb4_0900_ai_ci，所以"只改大小写"会被误判成重名。修法和
        // TagService.existsByNameExcept 完全一样，等 M2.3 收尾后单独处理。
        if (!collection.getName().equals(request.name())
                && existsByName(userId, request.name())) {
            throw new BusinessException(
                    ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
            );
        }

        collection.setName(request.name());
        collection.setDescription(request.description());
        collection.setUpdatedAt(now());

        try {
            linkCollectionMapper.updateById(collection);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ErrorCode.COLLECTION_NAME_ALREADY_EXISTS
            );
        }

        return toSummary(
                collection,
                countLinks(collectionId, userId)
        );
    }

    @Transactional
    public void deleteCollection(Long collectionId) {
        Long userId = currentUserId();
        LinkCollection collection = findOwned(collectionId, userId);

        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // [408] 判断顺序不能颠倒：Inbox 检查排在前面，
        // 所以"非空 Inbox"报的是更强的保护规则，而不是笼统的"集合非空"。
        if (Boolean.TRUE.equals(collection.getIsInbox())) {
            throw new BusinessException(
                    ErrorCode.COLLECTION_INBOX_PROTECTED
            );
        }

        // [业务] 集合里还有链接时拒绝删除：先让用户把链接搬走或确认，
        // 而不是连链接一起静默清掉。
        // [排错] 数据库外键是 ON DELETE CASCADE，它"允许"删；
        // 这条应用层规则才是真正拦住删除的那一道。
        if (countLinks(collectionId, userId) > 0) {
            throw new BusinessException(
                    ErrorCode.COLLECTION_NOT_EMPTY
            );
        }

        linkCollectionMapper.deleteById(collectionId);
    }

    private Long currentUserId() {
        return sessionTokenService.requireLoginId();
    }

    private LinkCollection findOwned(
            Long collectionId,
            Long userId
    ) {
        return linkCollectionMapper.selectOne(
                new LambdaQueryWrapper<LinkCollection>()
                        .eq(LinkCollection::getId, collectionId)
                        .eq(LinkCollection::getUserId, userId)
        );
    }

    private boolean existsByName(
            Long userId,
            String name
    ) {
        Long count = linkCollectionMapper.selectCount(
                new LambdaQueryWrapper<LinkCollection>()
                        .eq(LinkCollection::getUserId, userId)
                        .eq(LinkCollection::getName, name)
        );

        return count != null && count > 0;
    }

    /**
     * [408] 统计某个集合下的链接数量。查询条件以 collection_id 打头，
     * 正好命中 idx_link_collection_created 的首列，走的是索引而不是全表扫描。
     *
     * [软件工程] user_id 这一层是防御性的：调用方已经确认过集合归属，
     * 这里再兜一次，将来出现新的调用点时不会被漏掉。
     */
    private long countLinks(
            Long collectionId,
            Long userId
    ) {
        Long count = linkMapper.selectCount(
                new LambdaQueryWrapper<Link>()
                        .eq(Link::getCollectionId, collectionId)
                        .eq(Link::getUserId, userId)
        );

        return count == null ? 0L : count;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);
    }

    private CollectionSummary toSummary(
            LinkCollection collection,
            long linkCount
    ) {
        return new CollectionSummary(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                Boolean.TRUE.equals(collection.getIsInbox()),
                linkCount,
                collection.getCreatedAt()
                        .toInstant(ZoneOffset.UTC)
        );
    }
}