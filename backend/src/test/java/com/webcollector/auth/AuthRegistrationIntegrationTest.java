package com.webcollector.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.webcollector.auth.entity.AppUser;
import com.webcollector.auth.mapper.AppUserMapper;
import com.webcollector.support.SharedTestContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.auth.registration-enabled=true"
)
@AutoConfigureMockMvc
public class AuthRegistrationIntegrationTest {
    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserMapper appUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM app_user");
    }

    // [测试意图] 验证注册成功会真实写入用户并保存 BCrypt 哈希。
    @Test
    void shouldRegisterUserAndPersistBcryptHash() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "integration-student",
                                  "email": "integration@example.com",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.username")
                        .value("integration-student"))
                .andExpect(jsonPath("$.data.email")
                        .value("integration@example.com"))
                .andExpect(jsonPath("$.data.passwordHash")
                        .doesNotExist());

        AppUser saved = appUserMapper.selectOne(
                new LambdaQueryWrapper<AppUser>()
                        .eq(
                                AppUser::getUsername,
                                "integration-student"
                        )
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getEmail())
                .isEqualTo("integration@example.com");
        assertThat(saved.getPasswordHash())
                .isNotEqualTo("correct-horse");
        assertThat(passwordEncoder.matches(
                "correct-horse",
                saved.getPasswordHash()
        )).isTrue();
    }

    // [测试意图] 验证真实数据库下用户名重复返回 409。
    @Test
    void shouldRejectDuplicateUsername() throws Exception {
        register(
                "duplicate-user",
                "first@example.com"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "duplicate-user",
                                  "email": "second@example.com",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("USERNAME_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("用户名已存在"));
    }

    // [测试意图] 验证真实数据库下邮箱重复返回 409。
    @Test
    void shouldRejectDuplicateEmail() throws Exception {
        register(
                "email-owner",
                "shared@example.com"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "other-user",
                                  "email": "shared@example.com",
                                  "password": "correct-horse"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code")
                        .value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message")
                        .value("邮箱已被使用"));
    }

    // [辅助] 构造注册请求 JSON，避免测试重复填写相同字段。
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


}
