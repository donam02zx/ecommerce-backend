package com.service;

import com.dto.request.LoginRequest;
import com.dto.request.RefreshTokenRequest;
import com.dto.request.RegisterRequest;
import com.dto.response.AuthResponse;
import com.dto.response.UserResponse;
import com.entity.RefreshTokenEntity;
import com.entity.RoleEntity;
import com.entity.UserEntity;
import com.entity.UserRoleEntity;
import com.exception.AppException;
import com.repository.RefreshTokenRepository;
import com.repository.RoleRepository;
import com.repository.UserRepository;
import com.repository.UserRoleRepository;
import com.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict("Email already exists: " + request.getEmail());
        }
        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .active(true)
                .build();
        userRepository.save(user);

        RoleEntity customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> AppException.notFound("Role CUSTOMER not found"));
        UserRoleEntity userRole = UserRoleEntity.builder()
                .userId(user.getId()).roleId(customerRole.getId())
                .user(user).role(customerRole).build();
        userRoleRepository.save(userRole);

        log.info("User registered: email={}", user.getEmail());
        return UserResponse.from(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> AppException.badRequest("Invalid email or password"));

        if (!user.getActive()) throw AppException.badRequest("Account is disabled");

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("Invalid email or password");
        }

        // Tạo access token (JWT, ngắn hạn)
        String accessToken = jwtUtil.generateToken(user.getId(), user.getEmail());

        // Tạo refresh token (random string, dài hạn)
        String rawRefreshToken = generateRawToken();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .tokenHash(tokenHash)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        log.info("User logged in: email={}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)  // trả raw token về client
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .user(UserResponse.from(user))
                .build();
    }

    /**
     * Refresh flow — Token Rotation:
     * 1. Tìm refresh token trong DB theo hash
     * 2. Validate: không revoked, chưa hết hạn
     * 3. Revoke token cũ
     * 4. Cấp access token mới + refresh token mới
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshTokenEntity existingToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        if (existingToken.getRevoked()) {
            // Token đã bị revoke — có thể là replay attack
            // Revoke toàn bộ token của user để an toàn
            log.warn("Revoked token reuse detected for userId={}", existingToken.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(existingToken.getUser().getId());
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has been revoked");
        }

        if (existingToken.isExpired()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired, please login again");
        }

        UserEntity user = existingToken.getUser();
        if (!user.getActive()) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Account is disabled");
        }

        // Revoke token cũ (single-use)
        existingToken.setRevoked(true);
        refreshTokenRepository.save(existingToken);

        // Cấp access token mới
        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getEmail());

        // Cấp refresh token mới (rotation)
        String newRawRefreshToken = generateRawToken();
        String newTokenHash = hashToken(newRawRefreshToken);

        RefreshTokenEntity newRefreshToken = RefreshTokenEntity.builder()
                .tokenHash(newTokenHash)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtUtil.getRefreshExpirationMs()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(newRefreshToken);

        log.info("Token refreshed for userId={}", user.getId());

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMs() / 1000)
                .user(UserResponse.from(user))
                .build();
    }

    /**
     * Logout — revoke refresh token hiện tại
     */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("User logged out: userId={}", token.getUser().getId());
                });
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        return UserResponse.from(user);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Tạo random token 32 bytes → Base64 URL-safe string
     */
    private String generateRawToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash token bằng SHA-256 trước khi lưu vào DB
     * Không lưu raw token để giảm thiệt hại nếu DB bị leak
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}