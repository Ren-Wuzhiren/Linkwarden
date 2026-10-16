package com.webcollector.tag.mapper;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.collection.entity.LinkCollection;
import com.webcollector.collection.mapper.LinkCollectionMapper;
import com.webcollector.link.entity.Link;
import com.webcollector.link.mapper.LinkMapper;
import com.webcollector.support.SharedTestContainers;
import com.webcollector.tag.entity.LinkTag;
import com.webcollector.tag.entity.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class LinkTagMapperTest {
    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Autowired
    private LinkTagMapper linkTagMapper;

    @Autowired
    private LinkMapper linkMapper;

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private LinkCollectionMapper linkCollectionMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        // [软件工程] 一句 DELETE 就能清空四张表：app_user 的外键是 ON DELETE CASCADE，
        // 集合、链接、标签和它们之间的关联都会跟着消失。
        jdbcTemplate.update("DELETE FROM app_user");
    }

    // [测试意图] 验证关联插入后字段映射正确，且能按 link_id 读回来。
    @Test
    void shouldInsertAndSelectAssociation() {
        AppUser owner = newUser("link-tag-owner");
        LinkCollection collection = newCollection(owner.getId());
        Link link = newLink(owner.getId(), collection.getId(), "https://example.com/a");
        Tag tag = newTag(owner.getId(), "Java");
        LocalDateTime now = now();

        int affectedRows = linkTagMapper.insert(
                newLinkTag(link.getId(), tag.getId(), now)
        );

        assertThat(affectedRows).isEqualTo(1);

        List<LinkTag> loaded = linkTagMapper.selectList(
                new LambdaQueryWrapper<LinkTag>()
                        .eq(LinkTag::getLinkId, link.getId())
        );

        assertThat(loaded).hasSize(1);
        assertThat(loaded.get(0).getLinkId()).isEqualTo(link.getId());
        assertThat(loaded.get(0).getTagId()).isEqualTo(tag.getId());
        assertThat(loaded.get(0).getCreatedAt()).isEqualTo(now);
    }

    // [测试意图] 验证联合主键拒绝同一对 (link_id, tag_id) 出现两次。
    @Test
    void shouldRejectDuplicateAssociation() {
        AppUser owner = newUser("link-tag-duplicate-owner");
        LinkCollection collection = newCollection(owner.getId());
        Link link = newLink(owner.getId(), collection.getId(), "https://example.com/a");
        Tag tag = newTag(owner.getId(), "Java");

        linkTagMapper.insert(newLinkTag(link.getId(), tag.getId(), now()));

        assertThatThrownBy(() -> linkTagMapper.insert(
                newLinkTag(link.getId(), tag.getId(), now())
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // [测试意图] 验证删除标签只解除关联，链接本身完好——这是 M2.4 的验收标准。
    @Test
    void shouldKeepLinkWhenTagIsDeleted() {
        AppUser owner = newUser("link-tag-cascade-owner");
        LinkCollection collection = newCollection(owner.getId());
        Link link = newLink(owner.getId(), collection.getId(), "https://example.com/a");
        Tag tag = newTag(owner.getId(), "Java");

        linkTagMapper.insert(newLinkTag(link.getId(), tag.getId(), now()));

        tagMapper.deleteById(tag.getId());

        // [业务] 关联随 tag_id 外键的 ON DELETE CASCADE 消失。
        assertThat(countByTag(tag.getId())).isZero();
        // [业务] 关键断言：链接必须还活着。级联只清掉 link_tag 里的那一行，
        // 不会顺着 link_id 反向删除链接——这正是验收标准要的行为。
        assertThat(linkMapper.selectById(link.getId())).isNotNull();
    }

    // [测试意图] 验证删除链接时它身上的关联一起消失。
    @Test
    void shouldDeleteAssociationWhenLinkIsDeleted() {
        AppUser owner = newUser("link-tag-link-cascade-owner");
        LinkCollection collection = newCollection(owner.getId());
        Link link = newLink(owner.getId(), collection.getId(), "https://example.com/a");
        Tag tag = newTag(owner.getId(), "Java");

        linkTagMapper.insert(newLinkTag(link.getId(), tag.getId(), now()));

        linkMapper.deleteById(link.getId());

        assertThat(countByLink(link.getId())).isZero();
        // [业务] 标签不受影响：删链接不该顺手把标签也带走。
        assertThat(tagMapper.selectById(tag.getId())).isNotNull();
    }

    // [测试意图] 验证按 tag_id 统计只算该标签自己，不会把别的标签的关联算进来。
    @Test
    void shouldCountLinksPerTag() {
        AppUser owner = newUser("link-tag-count-owner");
        LinkCollection collection = newCollection(owner.getId());
        Link first = newLink(owner.getId(), collection.getId(), "https://example.com/a");
        Link second = newLink(owner.getId(), collection.getId(), "https://example.com/b");
        Tag java = newTag(owner.getId(), "Java");
        Tag go = newTag(owner.getId(), "Go");

        linkTagMapper.insert(newLinkTag(first.getId(), java.getId(), now()));
        linkTagMapper.insert(newLinkTag(second.getId(), java.getId(), now()));
        linkTagMapper.insert(newLinkTag(first.getId(), go.getId(), now()));

        assertThat(countByTag(java.getId())).isEqualTo(2L);
        assertThat(countByTag(go.getId())).isEqualTo(1L);
    }

    // [软件工程] 这两条统计与 TagService.countLinks 用的是同一种查询形状：
    // 条件只有 tag_id（或 link_id），走的是联合主键最左前缀
    // 与 InnoDB 为外键自动建的那个索引。
    private long countByTag(Long tagId) {
        Long count = linkTagMapper.selectCount(
                new LambdaQueryWrapper<LinkTag>()
                        .eq(LinkTag::getTagId, tagId)
        );

        return count == null ? 0L : count;
    }

    private long countByLink(Long linkId) {
        Long count = linkTagMapper.selectCount(
                new LambdaQueryWrapper<LinkTag>()
                        .eq(LinkTag::getLinkId, linkId)
        );

        return count == null ? 0L : count;
    }

    private AppUser newUser(String username) {
        LocalDateTime now = now();

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        assertThat(appUserMapper.insert(user)).isEqualTo(1);
        return user;
    }

    private LinkCollection newCollection(Long userId) {
        LocalDateTime now = now();

        LinkCollection collection = new LinkCollection();
        collection.setUserId(userId);
        collection.setName("Reading");
        collection.setDescription("");
        collection.setIsInbox(false);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);

        assertThat(linkCollectionMapper.insert(collection)).isEqualTo(1);
        return collection;
    }

    private Link newLink(Long userId, Long collectionId, String url) {
        LocalDateTime now = now();

        Link link = new Link();
        link.setUserId(userId);
        link.setCollectionId(collectionId);
        link.setOriginalUrl(url);
        link.setTitle("");
        link.setDescription("");
        link.setCreatedAt(now);
        link.setUpdatedAt(now);

        assertThat(linkMapper.insert(link)).isEqualTo(1);
        return link;
    }

    private Tag newTag(Long userId, String name) {
        LocalDateTime now = now();

        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);

        assertThat(tagMapper.insert(tag)).isEqualTo(1);
        return tag;
    }

    private static LinkTag newLinkTag(
            Long linkId,
            Long tagId,
            LocalDateTime now
    ) {
        LinkTag linkTag = new LinkTag();
        linkTag.setLinkId(linkId);
        linkTag.setTagId(tagId);
        linkTag.setCreatedAt(now);
        return linkTag;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC())
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
