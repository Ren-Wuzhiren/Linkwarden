package com.webcollector.auth.controller;

import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.service.AuthService;
import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.GlobalExceptionHandler;
import com.webcollector.common.web.RequestIdFilter;
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
}
