package com.webcollector.auth.service;

import com.webcollector.auth.config.AuthProperties;
import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class AuthServiceTest {
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-04T12:00:00Z");

    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    private AppUserMapper appUserMapper;
    private AuthProperties authProperties;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        appUserMapper = mock(AppUserMapper.class);
        authProperties = new AuthProperties();
        passwordEncoder = mock(PasswordEncoder.class);

        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        authService = new AuthService(
                appUserMapper,
                authProperties,
                passwordEncoder,
                clock
        );
    }

    // [测试意图] 验证注册关闭时立即拒绝且不访问 Mapper 或密码编码器。
    @Test
    void shouldRejectRegistrationWhenDisabled() {
        authProperties.setRegistrationEnabled(false);

        assertThatThrownBy(
                () -> authService.register(validRequest())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.REGISTRATION_DISABLED)
            );

        verifyNoInteractions(appUserMapper, passwordEncoder);
    }

    // [测试意图] 验证用户名重复时返回冲突且不执行插入和密码编码。
    @Test
    void shouldRejectDuplicateUsername() {
        authProperties.setRegistrationEnabled(true);

        when(appUserMapper.selectCount(any()))
                .thenReturn(1L);

        assertThatThrownBy(
                () -> authService.register(validRequest())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.USERNAME_ALREADY_EXISTS)
        );
    }

    // [测试意图] 验证邮箱重复时返回冲突且不执行插入和密码编码。
    @Test
    void shouldRejectDuplicateEmail() {
        authProperties.setRegistrationEnabled(true);

        when(appUserMapper.selectCount(any()))
                .thenReturn(0L, 1L);

        assertThatThrownBy(
                () -> authService.register(validRequest())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS)
        );

        verify(appUserMapper, never()).insert(any(AppUser.class));
        verifyNoInteractions(passwordEncoder);
    }

    // [测试意图] 验证注册使用 BCrypt 哈希密码并写入固定时间。
    @Test
    void shouldHashPasswordAndPersistUser() {
        authProperties.setRegistrationEnabled(true);

        when(appUserMapper.selectCount(any()))
                .thenReturn(0L);

        when(passwordEncoder.encode("correct-horse"))
                .thenReturn("$2a$10$hashed");

        when(appUserMapper.insert(any(AppUser.class)))
                .thenAnswer(invocation -> {
                    AppUser user = invocation.getArgument(0);
                    user.setId(1L);
                    return 1;
                });

        UserSummary summary =
                authService.register(validRequest());

        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.username()).isEqualTo("student");
        assertThat(summary.email())
                .isEqualTo("student@example.com");
        assertThat(summary.createdAt())
                .isEqualTo(FIXED_INSTANT);

        ArgumentCaptor<AppUser> captor =
                ArgumentCaptor.forClass(AppUser.class);

        verify(appUserMapper).insert(captor.capture());

        AppUser savedUser = captor.getValue();

        assertThat(savedUser.getPasswordHash())
                .isEqualTo("$2a$10$hashed");

        assertThat(savedUser.getPasswordHash())
                .isNotEqualTo("correct-horse");

        assertThat(savedUser.getCreatedAt())
                .isEqualTo(FIXED_TIME);

        assertThat(savedUser.getUpdatedAt())
                .isEqualTo(FIXED_TIME);

        verify(passwordEncoder).encode("correct-horse");
    }

    // [测试意图] 验证邮箱为空时不执行邮箱重复查询。
    @Test
    void shouldNotQueryEmailWhenEmailIsNull() {
        authProperties.setRegistrationEnabled(true);

        when(appUserMapper.selectCount(any()))
                .thenReturn(0L);

        when(passwordEncoder.encode(any()))
                .thenReturn("$2a$10$hashed");

        when(appUserMapper.insert(any(AppUser.class)))
                .thenReturn(1);

        authService.register(new RegisterRequest(
                "student",
                "   ",
                "correct-horse"
        ));

        verify(appUserMapper).selectCount(any());
    }

    // [测试意图] 验证数据库唯一键冲突翻译为 409 CONFLICT 而不是 500。
    @Test
    void shouldTranslateDuplicateKeyToConflict() {
        authProperties.setRegistrationEnabled(true);

        when(appUserMapper.selectCount(any()))
                .thenReturn(0L);

        when(passwordEncoder.encode("correct-horse"))
                .thenReturn("$2a$10$hashed");

        when(appUserMapper.insert(any(AppUser.class)))
                .thenThrow(new DuplicateKeyException("duplicate"));

        assertThatThrownBy(
                () -> authService.register(validRequest())
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT)
        );
    }

    private static RegisterRequest validRequest() {
        return new RegisterRequest(
                "student",
                "student@example.com",
                "correct-horse"
        );
    }

}
