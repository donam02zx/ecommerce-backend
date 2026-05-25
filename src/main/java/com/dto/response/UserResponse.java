package com.dto.response;

import com.entity.UserEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private String fullName;
    private String phone;
    private Boolean active;
    private Set<String> roles;
    private Instant createdAt;

    public static UserResponse from(UserEntity e) {
        return UserResponse.builder()
                .id(e.getId())
                .email(e.getEmail())
                .fullName(e.getFullName())
                .phone(e.getPhone())
                .active(e.getActive())
                .roles(e.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .collect(Collectors.toSet()))
                .createdAt(e.getCreatedAt())
                .build();
    }
}