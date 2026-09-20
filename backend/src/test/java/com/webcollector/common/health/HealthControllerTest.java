package com.webcollector.common.health;

import com.webcollector.common.web.BusinessException;
import com.webcollector.common.web.ErrorCode;
import com.webcollector.common.web.GlobalExceptionHandler;
import com.webcollector.common.web.RequestIdContext;
import com.webcollector.common.web.RequestIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerTest {

    private HealthCheckService healthCheckService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        healthCheckService = mock(HealthCheckService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new HealthController(healthCheckService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @AfterEach
    void cleanup() {
        RequestIdContext.clear();
        MDC.remove("requestId");
    }

    @Test
    void shouldReportLivenessWithoutCheckingDependencies() throws Exception {
        mockMvc.perform(get("/api/v1/health/live")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-health-001"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-health-001"
                ))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.message").value("服务存活"))
                .andExpect(jsonPath("$.requestId").value("req-health-001"));

        verifyNoInteractions(healthCheckService);
    }

    @Test
    void shouldReportReadyWhenDependenciesAreAvailable() throws Exception {
        when(healthCheckService.checkReadiness()).thenReturn(Map.of(
                "mysql", "UP",
                "redis", "UP"
        ));

        mockMvc.perform(get("/api/v1/health/ready")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-health-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mysql").value("UP"))
                .andExpect(jsonPath("$.data.redis").value("UP"))
                .andExpect(jsonPath("$.message").value("服务已就绪"))
                .andExpect(jsonPath("$.requestId").value("req-health-002"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenDependencyIsDown() throws Exception {
        when(healthCheckService.checkReadiness()).thenThrow(
                new BusinessException(
                        ErrorCode.SERVICE_UNAVAILABLE,
                        "服务依赖未就绪",
                        Map.of(
                                "mysql", "UP",
                                "redis", "DOWN"
                        )
                )
        );

        mockMvc.perform(get("/api/v1/health/ready")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-health-003"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("服务依赖未就绪"))
                .andExpect(jsonPath("$.details.mysql").value("UP"))
                .andExpect(jsonPath("$.details.redis").value("DOWN"))
                .andExpect(jsonPath("$.requestId").value("req-health-003"));
    }
}
