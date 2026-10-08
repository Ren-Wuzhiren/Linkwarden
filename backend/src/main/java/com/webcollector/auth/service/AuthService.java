package com.webcollector.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.config.AuthProperties;
import com.webcollector.auth.dto.LoginRequest;
import com.webcollector.auth.dto.LoginResponse;
import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.auth.session.SessionToken;
import com.webcollector.auth.session.SessionTokenService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final AppUserMapper appUserMapper;
    private final Clock clock;
    private final SessionTokenService sessionTokenService;

    public AuthService(
            AppUserMapper appUserMapper,
            AuthProperties authProperties,
            PasswordEncoder passwordEncoder,
            SessionTokenService sessionTokenService,
            Clock clock
    ) {
        this.appUserMapper = appUserMapper;
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.sessionTokenService = sessionTokenService;
        this.clock = clock;
    }

    private UserSummary toUserSummary(AppUser user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    @Transactional
    public UserSummary register(RegisterRequest request) {
        if (!authProperties.isRegistrationEnabled()) {
            throw new BusinessException(
                    ErrorCode.REGISTRATION_DISABLED
            );
        }

        if (existsByUsername(request.username())) {
            throw new BusinessException(
                    ErrorCode.USERNAME_ALREADY_EXISTS
            );
        }

        if (request.email() != null
                && existsByEmail(request.email())) {
            throw new BusinessException(
                    ErrorCode.EMAIL_ALREADY_EXISTS
            );
        }

        LocalDateTime now = LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MILLIS);

        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(
                passwordEncoder.encode(request.password())
        );
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        try {
            appUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "用户名或邮箱已存在"
            );
        }

        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                now.toInstant(ZoneOffset.UTC)
        );

    }

    private boolean existsByUsername(String username) {
        Long count = appUserMapper.selectCount(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getUsername, username)
        );

        return count != null && count > 0;
    }

    private boolean existsByEmail(String email) {
        Long count = appUserMapper.selectCount(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getEmail, email)
        );

        return count != null && count > 0;
    }

    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getUsername, request.username())
        );

        // [业务] 用户不存在和密码错误必须返回同一个错误，避免用户名枚举。
        if (user == null || !passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new BusinessException(
                    ErrorCode.AUTH_INVALID_CREDENTIALS
            );
        }

        SessionToken session = sessionTokenService.login(user.getId());

        Instant expireAt = Instant.now(clock)
                .plusSeconds(session.timeoutSeconds());

        return new LoginResponse(
                session.token(),
                session.tokenName(),
                expireAt,
                toUserSummary(user)
        );
    }

    public UserSummary getCurrentUser() {
        // requireLoginId() -> 由 SessionTokenService 检查未登录并读取用户 ID
        Long userId = sessionTokenService.requireLoginId();
        AppUser user = appUserMapper.selectById(userId);

        if (user == null) {
            // [业务] Token 有效但用户已不存在，清理会话并返回未认证。
            sessionTokenService.logout();
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }

        return toUserSummary(user);
    }

    public void logout() {
        // [软件工程] 具体注销细节由 SessionTokenService 适配器负责。
        sessionTokenService.logout();
    }
}
