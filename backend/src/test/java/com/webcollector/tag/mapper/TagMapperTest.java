package com.webcollector.tag.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.support.SharedTestContainers;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class TagMapperTest {
    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM app_user");
    }

    // [测试意图] 验证标签插入后主键回写、字段映射与时间精度正确。
    @Test
    void shouldInsertAndSelectTag() {
        AppUser owner = newUser("tag-owner");
        LocalDateTime now = now();

        Tag tag = newTag(owner.getId(), "Java", now);
        int affectedRows = tagMapper.insert(tag);

        assertThat(affectedRows).isEqualTo(1);
        assertThat(tag.getId()).isNotNull();

        Tag loaded = tagMapper.selectById(tag.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getUserId()).isEqualTo(owner.getId());
        assertThat(loaded.getName()).isEqualTo("Java");
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(loaded.getUpdatedAt()).isEqualTo(now);
    }

    // [测试意图] 验证同一用户不能创建两个同名标签。
    @Test
    void shouldRejectDuplicateTagNameForSameUser() {
        AppUser owner = newUser("tag-dup-owner");
        LocalDateTime now = now();

        tagMapper.insert(newTag(owner.getId(), "Java", now));

        assertThatThrownBy(() -> tagMapper.insert(
                newTag(owner.getId(), "Java", now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // [测试意图] 验证不同用户可以各有一个同名标签。
    @Test
    void shouldAllowDuplicateTagNameForDifferentUsers() {
        AppUser first = newUser("tag-first");
        AppUser second = newUser("tag-second");
        LocalDateTime now = now();

        Tag firstTag = newTag(first.getId(), "Java", now);
        Tag secondTag = newTag(second.getId(), "Java", now);

        assertThat(tagMapper.insert(firstTag)).isEqualTo(1);
        assertThat(tagMapper.insert(secondTag)).isEqualTo(1);
        assertThat(firstTag.getId()).isNotEqualTo(secondTag.getId());
    }

    // [测试意图] 验证 utf8mb4_0900_ai_ci 下标签名大小写不敏感：Java 与 java 会冲突。
    @Test
    void shouldTreatTagNameCaseInsensitively() {
        AppUser owner = newUser("tag-case-owner");
        LocalDateTime now = now();

        tagMapper.insert(newTag(owner.getId(), "Java", now));

        assertThatThrownBy(() -> tagMapper.insert(
                newTag(owner.getId(), "java", now)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    // [测试意图] 钉住排序规则：不排除自身时，用小写查询会命中大写那行。
    // 这条与下一条一起解释了"改名只改大小写会被误判为重复"的成因。
    @Test
    void shouldHitOwnRowWhenQueryingWithDifferentCase() {
        AppUser owner = newUser("tag-hit-owner");
        tagMapper.insert(newTag(owner.getId(), "Java", now()));

        Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, owner.getId())
                .eq(Tag::getName, "java"));

        assertThat(count).isEqualTo(1L);
    }

    // [测试意图] 验证排除自身后查重不再命中自己——改名只改大小写因此能通过。
    @Test
    void shouldNotHitOwnRowWhenExcludingOwnId() {
        AppUser owner = newUser("tag-exclude-owner");
        Tag tag = newTag(owner.getId(), "Java", now());
        tagMapper.insert(tag);

        Long count = tagMapper.selectCount(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getUserId, owner.getId())
                .eq(Tag::getName, "java")
                .ne(Tag::getId, tag.getId()));

        assertThat(count).isZero();
    }

    // [测试意图] 钉住 MyBatis-Plus 陷阱：ne(列, null) 会拼出 id <> NULL，
    // 该条件在 SQL 里恒为 unknown，使查重永远返回 0。TagService 必须跳过 null 排除。
    @Test
    void shouldNeverMatchWhenExcludingNull() {
        AppUser owner = newUser("tag-null-owner");
        tagMapper.insert(newTag(owner.getId(), "Java", now()));

        Long withNullExclude = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, owner.getId())
                        .eq(Tag::getName, "Java")
                        .ne(Tag::getId, null)
        );

        Long withoutExclude = tagMapper.selectCount(
                new LambdaQueryWrapper<Tag>()
                        .eq(Tag::getUserId, owner.getId())
                        .eq(Tag::getName, "Java")
        );

        assertThat(withNullExclude).isZero();
        assertThat(withoutExclude).isEqualTo(1L);
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

    private static Tag newTag(Long userId, String name, LocalDateTime now) {
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setCreatedAt(now);
        tag.setUpdatedAt(now);
        return tag;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(Clock.systemUTC())
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
