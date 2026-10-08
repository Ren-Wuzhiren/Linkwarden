package com.webcollector.auth;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import com.webcollector.support.SharedTestContainers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SaTokenRedisIntegrationTest {
    @DynamicPropertySource
    static void registerContainerProperties(
            DynamicPropertyRegistry registry
    ) {
        SharedTestContainers.registerProperties(registry);
    }

    @Test
    void shouldUseRedisDaoAndManageValueLifecycle() {
        SaTokenDao dao = SaManager.getSaTokenDao();
        assertThat(dao)
                .isInstanceOf(SaTokenDaoForRedisTemplate.class);

        String key = "test:sa-token:session";

        dao.set(key, "1001", 60);

        assertThat(dao.get(key)).isEqualTo("1001");
        assertThat(dao.getTimeout(key)).isBetween(1L, 60L);

        dao.delete(key);

        assertThat(dao.get(key)).isNull();
    }
}
