package com.webcollector.auth.service;

import com.webcollector.auth.config.AuthProperties;
import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.auth.dto.LoginRequest;
import com.webcollector.auth.dto.LoginResponse;
import com.webcollector.auth.session.SessionToken;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class AuthServiceTest {
    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-10-04T12:00:00Z");

    private static final LocalDateTime FIXED_TIME =
            LocalDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);

    private static final Instant FIXED_EXPIRES_AT =
            Instant.parse("2026-10-04T14:00:00Z");

    private AppUserMapper appUserMapper;
    private AuthProperties authProperties;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;
    private SessionTokenService sessionTokenService;

    @BeforeEach
    void setUp() {
        appUserMapper = mock(AppUserMapper.class);
        authProperties = new AuthProperties();
        passwordEncoder = mock(PasswordEncoder.class);
        sessionTokenService = mock(SessionTokenService.class);

        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        authService = new AuthService(
                appUserMapper,
                authProperties,
                passwordEncoder,
                sessionTokenService,
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

    // [测试意图] 验证用户名不存在时返回统一凭证错误且不建立会话。
    @Test
    void shouldRejectLoginWhenUsernameDoesNotExist() {
        when(appUserMapper.selectOne(any()))
                .thenReturn(null);

        assertThatThrownBy(
                () -> authService.login(new LoginRequest(
                        "missing-user",
                        "correct-horse"
                ))
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.AUTH_INVALID_CREDENTIALS
                        )
        );

        // 用户不存在时必须短路，不能继续密码校验，也不能建立会话。
        verifyNoInteractions(
                passwordEncoder,
                sessionTokenService
        );
    }

    // [测试意图] 验证密码错误时返回统一凭证错误且不建立会话。
    @Test
    void shouldRejectLoginWhenPasswordDoesNotMatch() {
        AppUser user = existingUser();

        when(appUserMapper.selectOne(any()))
                .thenReturn(user);

        when(passwordEncoder.matches(
                "wrong-password",
                user.getPasswordHash()
        )).thenReturn(false);

        assertThatThrownBy(
                () -> authService.login(new LoginRequest(
                        "student",
                        "wrong-password"
                ))
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                ErrorCode.AUTH_INVALID_CREDENTIALS
                        )
        );

        verify(sessionTokenService, never())
                .login(anyLong());
    }

    // [测试意图] 验证凭证正确时建立会话并返回 Token、过期时间和用户摘要。
    @Test
    void shouldReturnSessionAndUserSummaryWhenLoginSucceeds() {
        AppUser user = existingUser();

        when(appUserMapper.selectOne(any()))
                .thenReturn(user);

        when(passwordEncoder.matches(
                "correct-horse",
                user.getPasswordHash()
        )).thenReturn(true);

        when(sessionTokenService.login(1L))
                .thenReturn(new SessionToken(
                        "session-token",
                        "Authorization",
                        7200
                ));

        LoginResponse response = authService.login(
                new LoginRequest(
                        "  student  ",
                        "correct-horse"
                )
        );

        assertThat(response.token())
                .isEqualTo("session-token");
        assertThat(response.tokenName())
                .isEqualTo("Authorization");
        assertThat(response.expiresAt())
                .isEqualTo(FIXED_EXPIRES_AT);

        assertThat(response.user().id())
                .isEqualTo(1L);
        assertThat(response.user().username())
                .isEqualTo("student");
        assertThat(response.user().email())
                .isEqualTo("student@example.com");
        assertThat(response.user().createdAt())
                .isEqualTo(FIXED_INSTANT);

        verify(sessionTokenService).login(1L);
    }

    private static RegisterRequest validRequest() {
        return new RegisterRequest(
                "student",
                "student@example.com",
                "correct-horse"
        );
    }

    private static AppUser existingUser() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("student");
        user.setEmail("student@example.com");
        user.setPasswordHash("$2a$10$hashed");
        user.setCreatedAt(FIXED_TIME);
        user.setUpdatedAt(FIXED_TIME);
        return user;
    }

    // [测试意图] 验证已登录用户能够按 Token 中的用户 ID 读取当前用户摘要。
    @Test
    void shouldReturnCurrentUserWhenSessionExists() {
        AppUser user = existingUser();

        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(appUserMapper.selectById(1L))
                .thenReturn(user);

        UserSummary summary = authService.getCurrentUser();

        assertThat(summary.id()).isEqualTo(1L);
        assertThat(summary.username()).isEqualTo("student");
        assertThat(summary.email())
                .isEqualTo("student@example.com");
        assertThat(summary.createdAt())
                .isEqualTo(FIXED_INSTANT);

        verify(sessionTokenService).requireLoginId();
        verify(appUserMapper).selectById(1L);
    }

    // [测试意图] 验证会话用户已不存在时清理会话并返回未认证。
    @Test
    void shouldLogoutWhenCurrentUserNoLongerExists() {
        when(sessionTokenService.requireLoginId())
                .thenReturn(1L);

        when(appUserMapper.selectById(1L))
                .thenReturn(null);

        assertThatThrownBy(
                () -> authService.getCurrentUser()
        ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(ErrorCode.AUTH_REQUIRED)
        );

        verify(sessionTokenService).logout();
    }

    // [测试意图] 验证退出接口委托适配器注销当前会话。
    @Test
    void shouldDelegateLogoutToSessionTokenService() {
        authService.logout();

        verify(sessionTokenService).logout();
    }
}
