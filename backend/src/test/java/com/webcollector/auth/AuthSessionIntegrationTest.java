package com.webcollector.auth;

import com.jayway.jsonpath.JsonPath;
import com.webcollector.support.SharedTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.auth.registration-enabled=true"
)
@AutoConfigureMockMvc
public class AuthSessionIntegrationTest {
    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM app_user");
    }

    // [测试意图] 验证注册、登录、/me、退出后原 Token 失效的完整会话生命周期。
    @Test
    void shouldCompleteLoginMeLogoutLifecycle() throws Exception {
        register(
                "session-user",
                "session@example.com"
        );

        MvcResult loginResult = login(
                "session-user",
                "correct-horse"
        );

        String token = JsonPath.read(
                loginResult.getResponse().getContentAsString(),
                "$.data.token"
        );

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username")
                        .value("session-user"))
                .andExpect(jsonPath("$.data.email")
                        .value("session@example.com"))
                .andExpect(jsonPath("$.data.passwordHash")
                        .doesNotExist());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("退出成功"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                "Authorization",
                                "Bearer " + token
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message")
                        .value("请先登录"));
    }

    // [测试意图] 验证真实 BCrypt 校验下错误密码不会创建登录会话。
    @Test
    void shouldRejectInvalidCredentialsWithoutCreatingSession()
            throws Exception {
        register(
                "wrong-password-user",
                "wrong-password@example.com"
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "wrong-password-user",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message")
                        .value("用户名或密码错误"))
                .andExpect(jsonPath("$.data.token")
                        .doesNotExist());
    }

    // [测试意图] 验证未知 Token 被拒绝，且无 Token 退出保持幂等。
    @Test
    void shouldRejectUnknownTokenAndAllowIdempotentLogout()
            throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header(
                                "Authorization",
                                "Bearer unknown-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("AUTH_REQUIRED"));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("退出成功"));

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("退出成功"));
    }

    private void register(
            String username,
            String email
    ) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "correct-horse"
                                }
                                """.formatted(username, email)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(
            String username,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.tokenName")
                        .value("Authorization"))
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andReturn();
    }
}
