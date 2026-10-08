package com.webcollector.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/**
 * JVM 级共享的 Testcontainers 基础设施。
 *
 * <p>[软件工程] 集成测试类共享同一套 MySQL/Redis，
 * 避免每个测试类重复启动容器，缩短测试反馈时间。</p>
 */
public final class SharedTestContainers {

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("webcollector")
                    .withUsername("webcollector")
                    .withPassword("webcollector_dev");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine")
                    .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    private SharedTestContainers() {
    }

    public static void registerProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(6379)
        );
        registry.add("spring.data.redis.password", () -> "");
    }
}
