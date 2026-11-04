package com.xingshu.auth.controller;

import com.xingshu.auth.common.constants.JwtConstants;
import com.xingshu.auth.domain.dto.LoginFormDTO;
import com.xingshu.auth.domain.dto.LoginUserDTO;
import com.xingshu.auth.domain.dto.TokenResponse;
import com.xingshu.auth.interceptor.LoginUser;
import com.xingshu.auth.service.IAuthService;
import com.xingshu.common.base.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 * 参考 tjxt 项目 AccountController
 */
@Tag(name = "认证管理", description = "登录、登出、刷新 token")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginFormDTO loginForm) {
        TokenResponse token = authService.login(loginForm);
        return ApiResponse.success(token);
    }

    @Operation(summary = "刷新 token")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refreshToken(
            @RequestHeader(JwtConstants.REFRESH_HEADER) String refreshToken) {
        TokenResponse token = authService.refreshToken(refreshToken);
        return ApiResponse.success(token);
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@LoginUser LoginUserDTO user) {
        authService.logout(user.getUserId());
        return ApiResponse.<Void>success(null);
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public ApiResponse<LoginUserDTO> getCurrentUser(@LoginUser LoginUserDTO user) {
        return ApiResponse.success(user);
    }
}