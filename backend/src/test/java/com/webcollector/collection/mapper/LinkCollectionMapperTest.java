package com.webcollector.collection.mapper;

import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.collection.entity.LinkCollection;
import com.webcollector.support.SharedTestContainers;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LinkCollectionMapperTest {

    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Autowired
    private LinkCollectionMapper linkCollectionMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM app_user");
    }

    // [测试意图] 验证集合插入后主键回写、字段映射和 is_inbox 布尔值正确。
    @Test
    void shouldInsertAndSelectCollection() {
        AppUser owner = newUser("collection-owner");
        LocalDateTime now = now();

        LinkCollection collection = newCollection(
                owner.getId(),
                "Reading",
                true,
                now
        );

        int affectedRows = linkCollectionMapper.insert(collection);

        assertThat(affectedRows).isEqualTo(1);
        assertThat(collection.getId()).isNotNull();

        LinkCollection loaded = linkCollectionMapper.selectById(
                collection.getId()
        );

        assertThat(loaded).isNotNull();
        assertThat(loaded.getUserId()).isEqualTo(owner.getId());
        assertThat(loaded.getName()).isEqualTo("Reading");
        assertThat(loaded.getDescription()).isEmpty();
        assertThat(loaded.getIsInbox()).isTrue();
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(loaded.getUpdatedAt()).isEqualTo(now);
    }

    // [测试意图] 验证同一用户不能创建两个同名集合。
    @Test
    void shouldRejectDuplicateCollectionNameForSameUser() {
        AppUser owner = newUser("duplicate-owner");
        LocalDateTime now = now();

        linkCollectionMapper.insert(newCollection(
                owner.getId(),
                "Reading",
                false,
                now
        ));

        assertThatThrownBy(() -> linkCollectionMapper.insert(
                newCollection(
                        owner.getId(),
                        "Reading",
                        false,
                        now
                )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // [测试意图] 验证不同用户可以创建同名集合。
    @Test
    void shouldAllowDuplicateCollectionNameForDifferentUsers() {
        AppUser first = newUser("first-owner");
        AppUser second = newUser("second-owner");
        LocalDateTime now = now();

        LinkCollection firstCollection = newCollection(
                first.getId(),
                "Reading",
                false,
                now
        );

        LinkCollection secondCollection = newCollection(
                second.getId(),
                "Reading",
                false,
                now
        );

        assertThat(linkCollectionMapper.insert(firstCollection))
                .isEqualTo(1);
        assertThat(linkCollectionMapper.insert(secondCollection))
                .isEqualTo(1);
        assertThat(firstCollection.getId()).isNotNull();
        assertThat(secondCollection.getId()).isNotNull();
        assertThat(firstCollection.getId())
                .isNotEqualTo(secondCollection.getId());
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

    private static LinkCollection newCollection(
            Long userId,
            String name,
            boolean isInbox,
            LocalDateTime now
    ) {
        LinkCollection collection = new LinkCollection();
        collection.setUserId(userId);
        collection.setName(name);
        collection.setDescription("");
        collection.setIsInbox(isInbox);
        collection.setCreatedAt(now);
        collection.setUpdatedAt(now);
        return collection;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC())
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
