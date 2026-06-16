package com.controller;

import com.dto.request.LoginRequest;
import com.dto.request.RefreshTokenRequest;
import com.dto.request.RegisterRequest;
import com.dto.response.ApiResponse;
import com.dto.response.AuthResponse;
import com.dto.response.UserResponse;
import com.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Authentication APIs")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login — returns accessToken + refreshToken")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    /**
     * Dùng khi access token hết hạn (403):
     * 1. Client gửi refreshToken
     * 2. Server validate, revoke token cũ, cấp cặp token mới
     * 3. Client lưu token mới và dùng tiếp
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    /**
     * Logout — revoke refresh token
     * Access token vẫn valid đến khi hết hạn (stateless)
     */
    @PostMapping("/logout")
    @Operation(summary = "Logout — revoke refresh token",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Logged out successfully")
                .build();
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<UserResponse> profile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return ApiResponse.success(authService.getProfile(email));
    }
}