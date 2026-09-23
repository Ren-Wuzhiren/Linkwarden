package com.webcollector.common.web;

import cn.dev33.satoken.exception.NotLoginException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Test
    void shouldMapMethodArgumentNotValidException() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.details.name").value("名称不能为空"))
                .andExpect(jsonPath("$.requestId").value("req-handler-003"));
    }

    @Test
    void shouldMapConstraintViolationException() throws Exception {
        mockMvc.perform(get("/test/constraint")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-004"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("请求参数不合法"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value("req-handler-004"));
    }

    @Test
    void shouldMapMalformedJsonToInvalidRequest() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"name\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求格式不正确"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value("req-handler-005"));
    }

    @Test
    void shouldMapMethodArgumentTypeMismatchException() throws Exception {
        mockMvc.perform(get("/test/type-mismatch")
                        .param("id", "abc")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-006"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("请求格式不正确"))
                .andExpect(jsonPath("$.details.id").value("参数类型不正确"))
                .andExpect(jsonPath("$.requestId").value("req-handler-006"));
    }

    @Test
    void shouldHideUnknownExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unknown")
                        .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-007"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("系统内部错误"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value("req-handler-007"))
                .andExpect(content().string(not(containsString("secret internal details"))));
    }

    @Test
    void shouldMapNotLoginExceptionToAuthRequired() throws Exception {
        mockMvc.perform(get("/test/not-login")
                .header(RequestIdFilter.REQUEST_ID_HEADER, "req-handler-008"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        RequestIdFilter.REQUEST_ID_HEADER,
                        "req-handler-008"
                ))
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(jsonPath("$.requestId").value("req-handler-008"));
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

        @PostMapping("/test/validation")
        void validateBody(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/constraint")
        void throwConstraintViolation() {
            throw new ConstraintViolationException("参数校验失败", Set.of());
        }

        @GetMapping("/test/type-mismatch")
        void typeMismatch(@RequestParam Integer id) {
        }

        @GetMapping("/test/unknown")
        void throwUnknownException() {
            throw new IllegalStateException("secret internal details");
        }

        @GetMapping("/test/not-login")
        void throwNotLogin() {
            throw new NotLoginException(
                    NotLoginException.NOT_TOKEN_MESSAGE,
                    "login",
                    NotLoginException.NOT_TOKEN
            );
        }
    }

    private record TestRequest(
            @NotBlank(message = "名称不能为空")
            String name
    ) {
    }
}
