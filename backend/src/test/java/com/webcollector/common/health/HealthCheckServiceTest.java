package com.webcollector.common.health;

import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthCheckServiceTest {

    private DataSource dataSource;
    private Connection mysqlConnection;
    private RedisConnectionFactory redisConnectionFactory;
    private RedisConnection redisConnection;
    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() {
        dataSource = mock(DataSource.class);
        mysqlConnection = mock(Connection.class);
        redisConnectionFactory = mock(RedisConnectionFactory.class);
        redisConnection = mock(RedisConnection.class);

        healthCheckService = new HealthCheckService(
                dataSource,
                redisConnectionFactory
        );
    }

    @Test
    void shouldReportReadyWhenMysqlAndRedisAreAvailable() throws Exception {
        when(dataSource.getConnection()).thenReturn(mysqlConnection);
        when(mysqlConnection.isValid(2)).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        Map<String, String> result = healthCheckService.checkReadiness();

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "mysql", "UP",
                "redis", "UP"
        ));
        verify(mysqlConnection).close();
        verify(redisConnection).close();
    }

    @Test
    void shouldStillCheckRedisWhenMysqlIsDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("mysql down"));
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.ping()).thenReturn("PONG");

        assertThatThrownBy(() -> healthCheckService.checkReadiness())
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(exception.getDetails())
                            .containsEntry("mysql", "DOWN")
                            .containsEntry("redis", "UP");
                });

        verify(redisConnectionFactory).getConnection();
    }

    @Test
    void shouldReportMysqlUpWhenRedisIsDown() throws Exception {
        when(dataSource.getConnection()).thenReturn(mysqlConnection);
        when(mysqlConnection.isValid(2)).thenReturn(true);
        when(redisConnectionFactory.getConnection()).thenThrow(
                new RedisConnectionFailureException("redis down")
        );

        assertThatThrownBy(() -> healthCheckService.checkReadiness())
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(exception.getDetails())
                            .containsEntry("mysql", "UP")
                            .containsEntry("redis", "DOWN");
                });
    }

    @Test
    void shouldReportBothDependenciesDown() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("mysql down"));
        when(redisConnectionFactory.getConnection()).thenThrow(
                new RedisConnectionFailureException("redis down")
        );

        assertThatThrownBy(() -> healthCheckService.checkReadiness())
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(exception.getDetails())
                            .containsEntry("mysql", "DOWN")
                            .containsEntry("redis", "DOWN");
                });
    }
}
