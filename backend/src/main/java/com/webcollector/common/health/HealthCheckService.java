package com.webcollector.common.health;

import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.RequestIdContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HealthCheckService {
    private static final Logger log =
            LoggerFactory.getLogger(HealthCheckService.class);

    private static final int DATABASE_VALIDATION_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    public HealthCheckService(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory
    ) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public Map<String, String> checkReadiness() {
        boolean mysqlUp = isMysqlUp();
        boolean redisUp = isRedisUp();

        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("mysql", mysqlUp ? "UP" : "DOWN");
        checks.put("redis", redisUp ? "UP" : "DOWN");

        if (!mysqlUp || !redisUp) {
            throw new BusinessException(
                    ErrorCode.SERVICE_UNAVAILABLE,
                    "服务依赖未就绪",
                    checks
            );
        }

        return Map.copyOf(checks);
    }

    private boolean isMysqlUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_VALIDATION_TIMEOUT_SECONDS);
        } catch (Exception exception) {
            log.warn(
                    "MySQL readiness check failed. requestId={}, error={}",
                    RequestIdContext.get(),
                    exception.getClass().getSimpleName()
            );
            log.debug("MySQL readiness check failure details", exception);
            return false;
        }
    }

    private boolean isRedisUp() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception exception) {
            log.warn(
                    "Redis readiness check failed. requestId={}, error={}",
                    RequestIdContext.get(),
                    exception.getClass().getSimpleName()
            );
            log.debug("Redis readiness check failure details", exception);
            return false;
        }
    }
}
