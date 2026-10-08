package com.webcollector.auth.controller;

import com.webcollector.auth.dto.LoginRequest;
import com.webcollector.auth.dto.LoginResponse;
import com.webcollector.auth.dto.RegisterRequest;
import com.webcollector.auth.dto.UserSummary;
import com.webcollector.auth.service.AuthService;
import com.webcollector.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


/**
 * 认证接口入口。
 *
 * <p>[软件工程] Controller/Adapter：
 * 负责 HTTP 请求和响应协议转换，
 * 不承载密码策略、事务和数据库访问。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册新用户。
     *
     * [软件工程] 契约边界：
     * Bean Validation 负责请求字段校验，
     * AuthService 负责注册业务规则，
     * Controller 只负责调用和包装响应。
     *
     * @param request 已通过 Bean Validation 校验的注册请求
     * @return 201 Created 和统一的成功响应
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserSummary>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        // [软件工程] Controller 不处理事务、密码哈希和数据库异常。
        UserSummary user = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(user, "注册成功"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(
                        authService.login(request),
                        "登录成功"
                )
        );
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me() {
        return ApiResponse.success(authService.getCurrentUser());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null, "退出成功");
    }
}
