package com.webcollector.auth.controller;

import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.service.AuthService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.GlobalExceptionHandler;
import com.webcollector.common.web.RequestIdFilter;
import com.webcollector.auth.dto.LoginRequest;
import com.webcollector.auth.dto.LoginResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.times;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    // [测试意图] 验证合法注册请求返回 201、统一响应且响应不泄漏 passwordHash。
    @Test
    void shouldReturnCreatedWhenRegistrationSucceeds() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new UserSummary(
                        1L,
                        "student",
                        "student@example.com",
                        Instant.parse("2026-10-07T12:00:00Z")
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-001"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "  student  ",
                                  "email": "  Student@Example.COM  ",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-auth-001"
                ))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("student"))
                .andExpect(jsonPath("$.data.email")
                        .value("student@example.com"))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-10-07T12:00:00Z"))
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-001"))
                .andExpect(jsonPath("$.data.passwordHash")
                        .doesNotExist());

        ArgumentCaptor<RegisterRequest> captor =
                ArgumentCaptor.forClass(RegisterRequest.class);

        verify(authService).register(captor.capture());

        RegisterRequest captured = captor.getValue();

        assertThat(captured.username()).isEqualTo("student");
        assertThat(captured.email()).isEqualTo("student@example.com");
        assertThat(captured.password()).isEqualTo("correct-horse");
    }

    // [测试意图] 验证字段校验失败返回 400 VALIDATION_ERROR 且不会调用 AuthService。
    @Test
    void shouldReturnValidationErrorWhenRequestIsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-002"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "ab",
                                  "email": "not-an-email",
                                  "password": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.username")
                        .value("用户名长度必须在 3 到 32 个字符之间"))
                .andExpect(jsonPath("$.details.email")
                        .value("邮箱格式不正确"))
                .andExpect(jsonPath("$.details.password")
                        .value("密码长度必须在 8 到 72 个字符之间"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-002"));

        verifyNoInteractions(authService);
    }

    // [测试意图] 验证注册关闭时返回 403 REGISTRATION_DISABLED。
    @Test
    void shouldReturnForbiddenWhenRegistrationIsDisabled() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.REGISTRATION_DISABLED
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-003"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student",
                                  "email": "student@example.com",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code")
                        .value("REGISTRATION_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value("当前环境未开放注册"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-003"));
    }

    // [测试意图] 验证用户名冲突返回 409 USERNAME_ALREADY_EXISTS。
    @Test
    void shouldReturnConflictWhenUsernameExists() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.USERNAME_ALREADY_EXISTS
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-004"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student",
                                  "email": "student@example.com",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("用户名已存在"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-004"));
    }

    // [测试意图] 验证邮箱冲突返回 409 EMAIL_ALREADY_EXISTS。
    @Test
    void shouldReturnConflictWhenEmailExists() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.EMAIL_ALREADY_EXISTS
                ));

        mockMvc.perform(post("/api/v1/auth/register")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-005"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "student",
                                  "email": "student@example.com",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("邮箱已被使用"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-005"));
    }

    // [测试意图] 验证非法 JSON 返回 400 INVALID_REQUEST 而不是 500。
    @Test
    void shouldReturnInvalidRequestWhenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-006"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message")
                        .value("请求格式不正确"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-006"));
    }

    // [测试意图] 验证登录成功返回 200、Token、过期时间和用户摘要。
    @Test
    void shouldReturnLoginResponseWhenCredentialsAreValid() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(
                        "session-token",
                        "Authorization",
                        Instant.parse("2026-10-08T14:00:00Z"),
                        new UserSummary(
                                1L,
                                "student",
                                "student@example.com",
                                Instant.parse("2026-10-08T12:00:00Z")
                        )
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-007"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "  student  ",
                              "password": "correct-horse"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token")
                        .value("session-token"))
                .andExpect(jsonPath("$.data.tokenName")
                        .value("Authorization"))
                .andExpect(jsonPath("$.data.expiresAt")
                        .value("2026-10-08T14:00:00Z"))
                .andExpect(jsonPath("$.data.user.id")
                        .value(1))
                .andExpect(jsonPath("$.data.user.username")
                        .value("student"))
                .andExpect(jsonPath("$.message")
                        .value("登录成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-007"))
                .andExpect(jsonPath("$.data.user.passwordHash")
                        .doesNotExist());

        ArgumentCaptor<LoginRequest> captor =
                ArgumentCaptor.forClass(LoginRequest.class);

        verify(authService).login(captor.capture());

        assertThat(captor.getValue().username())
                .isEqualTo("student");
        assertThat(captor.getValue().password())
                .isEqualTo("correct-horse");
    }

    // [测试意图] 验证用户名或密码错误返回 401 AUTH_INVALID_CREDENTIALS。
    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_INVALID_CREDENTIALS
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-008"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "student",
                              "password": "wrong-password"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("用户名或密码错误"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-008"));
    }

    // [测试意图] 验证登录请求字段非法时返回 400 且不会调用 AuthService。
    @Test
    void shouldReturnValidationErrorWhenLoginRequestIsInvalid()
            throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-009"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "",
                              "password": ""
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.username")
                        .value("用户名不能为空"))
                .andExpect(jsonPath("$.details.password")
                        .value("密码不能为空"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-009"));

        verifyNoInteractions(authService);
    }

    // [测试意图] 验证有效会话可以读取当前用户摘要。
    @Test
    void shouldReturnCurrentUserWhenSessionIsValid() throws Exception {
        when(authService.getCurrentUser())
                .thenReturn(new UserSummary(
                        1L,
                        "student",
                        "student@example.com",
                        Instant.parse("2026-10-08T12:00:00Z")
                ));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-010"
                        )
                        .header(
                                "Authorization",
                                "Bearer session-token"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id")
                        .value(1))
                .andExpect(jsonPath("$.data.username")
                        .value("student"))
                .andExpect(jsonPath("$.data.email")
                        .value("student@example.com"))
                .andExpect(jsonPath("$.data.createdAt")
                        .value("2026-10-08T12:00:00Z"))
                .andExpect(jsonPath("$.data.passwordHash")
                        .doesNotExist())
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-010"));

        verify(authService).getCurrentUser();
    }

    // [测试意图] 验证缺失或无效 Token 时返回 401 AUTH_REQUIRED。
    @Test
    void shouldReturnUnauthorizedWhenCurrentUserIsNotLoggedIn()
            throws Exception {
        when(authService.getCurrentUser())
                .thenThrow(new BusinessException(
                        ErrorCode.AUTH_REQUIRED
                ));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-011"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("请先登录"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-011"));
    }

    // [测试意图] 验证退出成功返回统一响应。
    @Test
    void shouldReturnSuccessWhenLogoutSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-012"
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("退出成功"))
                .andExpect(jsonPath("$.requestId")
                        .value("req-auth-012"));

        verify(authService).logout();
    }

    // [测试意图] 验证重复退出保持幂等且两次都返回成功。
    @Test
    void shouldAllowRepeatedLogout() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-013"
                        ))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(
                                RequestIdFilter.REQUEST_ID_HEADER,
                                "req-auth-014"
                        ))
                .andExpect(status().isOk());

        verify(authService, times(2)).logout();
    }
}
