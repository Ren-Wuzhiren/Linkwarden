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
    private final SessionTokenService sessionTokenService;
    private final Clock clock;

    public CollectionService(
            LinkCollectionMapper linkCollectionMapper,
            SessionTokenService sessionTokenService,
            Clock clock
    ) {
        this.linkCollectionMapper = linkCollectionMapper;
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

    @Transactional(readOnly = true)
    public List<CollectionSummary> listCollections() {
        Long userId = currentUserId();

        return linkCollectionMapper.selectList(
                new LambdaQueryWrapper<LinkCollection>()
                        .eq(LinkCollection::getUserId, userId)
                        .orderByAsc(LinkCollection::getId)
        )
                .stream()
                .map(this::toSummary)
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

        return toSummary(collection);
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

        if (!collection.getName().equals(request.name()) && existsByName(userId, request.name())) {
            throw new BusinessException(ErrorCode.COLLECTION_NAME_ALREADY_EXISTS);
        }

        collection.setName(request.name());

        collection.setDescription(request.description());
        collection.setUpdatedAt(now());

        try {
            linkCollectionMapper.updateById(collection);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorCode.COLLECTION_NAME_ALREADY_EXISTS);
        }

        return toSummary(collection);
    }

    @Transactional
    public void deleteCollection(Long collectionId) {
        Long userId = currentUserId();
        LinkCollection collection = findOwned(
                collectionId,
                userId
        );

        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        if (Boolean.TRUE.equals(collection.getIsInbox())) {
            throw new BusinessException(ErrorCode.COLLECTION_INBOX_PROTECTED);
        }

        linkCollectionMapper.deleteById(collectionId);
    }


    // [业务] link 表落地后，这里需要补充“集合仍有关联链接则拒绝删除”的判断。

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

    private LocalDateTime now() {
        return LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);
    }

    private CollectionSummary toSummary(
            LinkCollection collection
    ) {
        return new CollectionSummary(
                collection.getId(),
                collection.getName(),
                collection.getDescription(),
                Boolean.TRUE.equals(collection.getIsInbox()),
                0L,
                collection.getCreatedAt()
                        .toInstant(ZoneOffset.UTC)
        );
    }
}