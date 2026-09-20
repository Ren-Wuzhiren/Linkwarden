package com.webcollector.common.health;

import com.webcollector.common.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final HealthCheckService healthCheckService;

    public HealthController(HealthCheckService healthCheckService) {
        this.healthCheckService = healthCheckService;
    }

    @GetMapping("/live")
    public ApiResponse<Map<String, String>> live() {
        return ApiResponse.success(
                Map.of("status", "UP"),
                "服务存活"
        );
    }

    @GetMapping("/ready")
    public ApiResponse<Map<String, String>> ready() {
        return ApiResponse.success(
                healthCheckService.checkReadiness(),
                "服务已就绪"
        );
    }
}
