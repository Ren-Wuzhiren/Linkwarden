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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * [软件工程] M2.4 的验收标准是"一条链接能挂上多个标签，删标签只解除关联不删链接"。
 *
 * 这个证明只能在这里做：真实 MySQL + 真实 Sa-Token/Redis 会话 + 真实 Service。
 * LinkTagMapperTest 能证明级联确实那样工作，但要证明"从 HTTP 入口走完
 * POST /links、PUT /links/{id}/tags 与 DELETE /tags/{id} 之后链接依然可读"，
 * 只有端到端跑一遍。
 *
 * [软件工程] 观察角度是刻意反过来选的：LinkSummary 不返回标签，所以本轮
 * 只能从标签那一侧（GET /tags 的 linkCount）反向确认关联真的落库了。
 * 某条链接挂了哪些标签要等 M6.3 / M7.3 才有读接口。
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "app.auth.registration-enabled=true"
)
@AutoConfigureMockMvc
public class LinkTagAssociationIntegrationTest {
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

    // [测试意图] 验证一条链接能同时挂上多个标签，且 PUT 的替换语义真的生效。
    @Test
    void shouldAttachMultipleTagsAndReplaceThem() throws Exception {
        String token = registerAndLogin("tag-attach-owner");
        long javaId = createTag(token, "Java");
        long goId = createTag(token, "Go");

        long linkId = saveLinkWithTags(
                token,
                defaultInboxId(token),
                "https://example.com/a",
                javaId,
                goId
        );

        // [业务] 保存时带上两个标签，两个计数都必须是 1。
        assertThat(linkCountOf(token, "Java")).isEqualTo(1L);
        assertThat(linkCountOf(token, "Go")).isEqualTo(1L);

        // [业务] 再用 PUT 把它换成只剩 Java：Go 的计数必须归零。
        // 这是"替换"而不是"追加"的端到端证据——追加的话 Go 会一直是 1。
        mockMvc.perform(put("/api/v1/links/" + linkId + "/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagIds": [%d]
                                }
                                """.formatted(javaId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("标签已更新"));

        assertThat(linkCountOf(token, "Java")).isEqualTo(1L);
        assertThat(linkCountOf(token, "Go")).isZero();
    }

    // [测试意图] 验证删标签之后链接仍然可读——M2.4 验收标准的端到端证明。
    @Test
    void shouldKeepLinkReadableAfterItsTagIsDeleted() throws Exception {
        String token = registerAndLogin("tag-delete-owner");
        long tagId = createTag(token, "Java");
        long linkId = saveLinkWithTags(
                token,
                defaultInboxId(token),
                "https://example.com/a",
                tagId
        );

        mockMvc.perform(delete("/api/v1/tags/" + tagId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // [业务] 标签没了，但链接必须完好：级联只清 link_tag 里的那一行。
        mockMvc.perform(get("/api/v1/links/" + linkId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalUrl")
                        .value("https://example.com/a"));

        // [业务] 另一半：标签列表里确实已经没有它了。
        String tagsBody = mockMvc.perform(get("/api/v1/tags")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat((List<?>) JsonPath.read(tagsBody, "$.data")).isEmpty();
    }

    // [测试意图] 验证挂不上别人的标签，且失败之后原有关联一根手指都没被碰过。
    @Test
    void shouldRejectAnotherUsersTagWithoutTouchingExistingOnes()
            throws Exception {
        String aliceToken = registerAndLogin("tag-association-alice");
        String bobToken = registerAndLogin("tag-association-bob");

        long aliceTagId = createTag(aliceToken, "Java");
        long bobTagId = createTag(bobToken, "Kotlin");

        long aliceLinkId = saveLinkWithTags(
                aliceToken,
                defaultInboxId(aliceToken),
                "https://alice.example.com/a",
                aliceTagId
        );

        mockMvc.perform(put("/api/v1/links/" + aliceLinkId + "/tags")
                        .header("Authorization", "Bearer " + aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tagIds": [%d]
                                }
                                """.formatted(bobTagId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // [业务] 这条断言守的是"先校验、后动数据"的顺序：换成"先清空旧关联
        // 再校验新标签"的写法，Alice 原来的关联会被删掉，这里就会读到 0。
        assertThat(linkCountOf(aliceToken, "Java")).isEqualTo(1L);
        // [业务] Bob 的标签始终没被用过。
        assertThat(linkCountOf(bobToken, "Kotlin")).isZero();
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

    private long createTag(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s"
                                }
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    // [业务] 保存链接时一次带上若干标签，走的是 POST /links 的 tagIds 字段。
    private long saveLinkWithTags(
            String token,
            long collectionId,
            String url,
            long... tagIds
    ) throws Exception {
        String tagIdsJson = Arrays.stream(tagIds)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));

        String body = mockMvc.perform(post("/api/v1/links")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "url": "%s",
                                  "collectionId": %d,
                                  "tagIds": [%s],
                                  "title": "",
                                  "description": ""
                                }
                                """.formatted(url, collectionId, tagIdsJson)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(body, "$.data.id");
        return id.longValue();
    }

    // [软件工程] 从标签这一侧反向观察关联：LinkSummary 不返回标签，
    // 所以"关联是否真的写进了 link_tag"只能靠这个计数来证明。
    private long linkCountOf(String token, String tagName) throws Exception {
        String body = mockMvc.perform(get("/api/v1/tags")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Number> counts = JsonPath.read(
                body,
                "$.data[?(@.name == '" + tagName + "')].linkCount"
        );

        assertThat(counts)
                .as("标签 [%s] 应当存在于当前用户的标签列表里", tagName)
                .hasSize(1);

        return counts.get(0).longValue();
    }
}
