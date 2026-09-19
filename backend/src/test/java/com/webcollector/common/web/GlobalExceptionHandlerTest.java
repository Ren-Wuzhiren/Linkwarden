package com.webcollector.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void shouldMapBusinessExceptionToErrorResponse() throws Exception {
        mockMvc.perform(get("/test/business-conflict")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-001"))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-handler-001"
                ))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("用户名冲突"))
                .andExpect(jsonPath("$.details.username").value("用户名已存在"))
                .andExpect(jsonPath("$.requestId").value("req-handler-001"));
    }

    @Test
    void shouldUseErrorCodeStatusForBusinessException() throws Exception {
        mockMvc.perform(get("/test/business-not-found")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-002"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("资源不存在"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value("req-handler-002"));
    }

    @RestController
    private static class TestController {

        @GetMapping("/test/business-conflict")
        void throwConflict() {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    "用户名冲突",
                    Map.of("username", "用户名已存在")
            );
        }

        @GetMapping("/test/business-not-found")
        void throwNotFound() {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }
}
