package com.service;

import com.dto.request.LoginRequest;
import com.dto.request.RegisterRequest;
import com.dto.response.AuthResponse;
import com.dto.response.UserResponse;
import com.entity.RoleEntity;
import com.entity.UserEntity;
import com.entity.UserRoleEntity;
import com.exception.AppException;
import com.repository.RoleRepository;
import com.repository.UserRepository;
import com.repository.UserRoleRepository;
import com.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
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

        // Gán role CUSTOMER mặc định
        RoleEntity customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> AppException.notFound("Role CUSTOMER not found"));

        UserRoleEntity userRole = UserRoleEntity.builder()
                .userId(user.getId())
                .roleId(customerRole.getId())
                .user(user)
                .role(customerRole)
                .build();
        userRoleRepository.save(userRole);

        log.info("User registered: email={}", user.getEmail());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> AppException.badRequest("Invalid email or password"));

        if (!user.getActive()) {
            throw AppException.badRequest("Account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getEmail());
        log.info("User logged in: email={}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.from(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(String email) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> AppException.notFound("User not found"));
        return UserResponse.from(user);
    }
}