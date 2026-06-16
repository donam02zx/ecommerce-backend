package com.controller;

import com.dto.request.LoginRequest;
import com.dto.request.RefreshTokenRequest;
import com.dto.request.RegisterRequest;
import com.dto.response.ApiResponse;
import com.dto.response.AuthResponse;
import com.dto.response.UserResponse;
import com.entity.UserEntity;
import com.exception.AppException;
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

    private final AuthService authService;  // Chỉ dùng Service, KHÔNG dùng Repository

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

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout — revoke both access and refresh token",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(authorizationHeader, request);
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

    @PostMapping("/revoke-all")
    @Operation(summary = "Revoke all tokens (for password change or security breach)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ApiResponse<Void> revokeAllTokens() {
        // Lấy email từ Security Context
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        // Gọi Service để revoke tất cả token
        authService.revokeAllTokensByEmail(email);

        return ApiResponse.<Void>builder()
                .success(true)
                .message("All tokens have been revoked. Please login again.")
                .build();
    }
}