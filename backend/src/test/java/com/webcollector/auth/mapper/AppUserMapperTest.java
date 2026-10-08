package com.webcollector.auth.mapper;


import com.webcollector.auth.entity.AppUser;
import com.webcollector.support.SharedTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AppUserMapperTest {
    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Autowired
    private AppUserMapper appUserMapper;

    @Test
    void shouldInsertAndSelectAppUser() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC())
                .truncatedTo(ChronoUnit.MILLIS);

        AppUser user = newUser("mapper-student", now);

        int affectedRows = appUserMapper.insert(user);

        assertThat(affectedRows).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        AppUser loaded = appUserMapper.selectById(user.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo("mapper-student");
        assertThat(loaded.getEmail()).isEqualTo("mapper-student@example.com");
        assertThat(loaded.getPasswordHash()).isEqualTo("$2a$10$placeholder");
        assertThat(loaded.getCreatedAt()).isEqualTo(now);
        assertThat(loaded.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void shouldRejectDuplicateUsernameAtDatabaseLevel() {
        LocalDateTime now = LocalDateTime.now(Clock.systemUTC())
                .truncatedTo(ChronoUnit.MILLIS);

        appUserMapper.insert(newUser("duplicated-user", now));

        AppUser anotherUser = newUser("duplicated-user", now);
        anotherUser.setEmail("another-email@example.com");

        assertThatThrownBy(() -> appUserMapper.insert(anotherUser))
                .isInstanceOf(DataIntegrityViolationException.class);

    }

    private static AppUser newUser(String username, LocalDateTime now) {
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$placeholder");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        return user;
    }
}
