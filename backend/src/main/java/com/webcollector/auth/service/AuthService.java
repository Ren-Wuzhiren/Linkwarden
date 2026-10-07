package com.webcollector.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.config.AuthProperties;
import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final AppUserMapper appUserMapper;
    private final Clock clock;

    public AuthService(
            AppUserMapper appUserMapper,
            AuthProperties authProperties,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.appUserMapper = appUserMapper;
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
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
}
