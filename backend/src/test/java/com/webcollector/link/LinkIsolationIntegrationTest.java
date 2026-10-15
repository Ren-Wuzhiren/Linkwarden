package com.webcollector.link;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [软件工程] M2.3 的验收标准是"同一用户下能存下链接且只看到自己的"。
 *
 * 这个证明只能在这里做：真实 MySQL + 真实 Sa-Token/Redis 会话 + 真实 LinkService。
 * 纯 Mockito 的单测拦不住"listLinks 忘记带 user_id"，因为 Mock 会把查询条件整个吞掉；
 * 而 Mapper 测试又只能证明"测试自己 new 出来的 wrapper 管用"，
 * 证明不了"Service 记得拼这个条件"。只有从 HTTP 入口一路打到数据库才能覆盖这段。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.auth.registration-enabled=true"
)
@AutoConfigureMockMvc
public class LinkIsolationIntegrationTest {
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

    // [测试意图] 验证两个用户各自的链接列表互不串味。
    @Test
    void shouldListOnlyOwnLinks() throws Exception {
        String aliceToken = registerAndLogin("link-alice");
        String bobToken = registerAndLogin("link-bob");

        saveLink(aliceToken, defaultInboxId(aliceToken), "https://a.example.com/1");
        saveLink(bobToken, defaultInboxId(bobToken), "https://b.example.com/1");

        mockMvc.perform(get("/api/v1/links")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalUrl")
                        .value("https://a.example.com/1"));

        mockMvc.perform(get("/api/v1/links")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].originalUrl")
                        .value("https://b.example.com/1"));
    }

    // [测试意图] 验证 A 既读不到、也改不掉、更删不掉 B 的链接，且 B 的链接完好无损。
    @Test
    void shouldNotReadModifyOrDeleteOtherUsersLink() throws Exception {
        String aliceToken = registerAndLogin("link-reader-alice");
        String bobToken = registerAndLogin("link-reader-bob");

        long bobLinkId = saveLink(
                bobToken,
                defaultInboxId(bobToken),
                "https://b.example.com/secret"
        );

        mockMvc.perform(get("/api/v1/links/" + bobLinkId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/links/" + bobLinkId)
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "collectionId": %d,
                                  "title": "被篡改",
                                  "description": ""
                                }
                                """.formatted(defaultInboxId(aliceToken))))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/links/" + bobLinkId)
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isNotFound());

        // [业务] 三次越权都没有产生副作用：B 依然能读到自己那条，标题也没被改。
        mockMvc.perform(get("/api/v1/links/" + bobLinkId)
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl")
                        .value("https://b.example.com/secret"))
                .andExpect(jsonPath("$.data.collectionId")
                        .value(defaultInboxId(bobToken)))
                .andExpect(jsonPath("$.data.title").value(""));
    }

    // [测试意图] 验证不能把链接塞进别人的集合，且数据库里不会留下任何一条。
    @Test
    void shouldRejectLinkIntoOtherUsersCollection() throws Exception {
        String aliceToken = registerAndLogin("link-collection-alice");
        String bobToken = registerAndLogin("link-collection-bob");

        long bobInboxId = defaultInboxId(bobToken);

        mockMvc.perform(post("/api/v1/links")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "https://alice.example.com/intruder",
                                  "collectionId": %d
                                }
                                """.formatted(bobInboxId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/v1/links")
                        .header("Authorization", "Bearer " + aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/links")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s@example.com",
                                  "password": "correct-horse"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "correct-horse"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(body, "$.data.token");
    }

    // [业务] 注册时同事务会建好默认 Inbox，所以这里不用自己创建集合。
    private long defaultInboxId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/collections")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.data[0].id");
        return id.longValue();
    }

    private long saveLink(
            String token,
            long collectionId,
            String url
    ) throws Exception {
        String body = mockMvc.perform(post("/api/v1/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "%s",
                                  "collectionId": %d,
                                  "title": "",
                                  "description": ""
                                }
                                """.formatted(url, collectionId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }
}