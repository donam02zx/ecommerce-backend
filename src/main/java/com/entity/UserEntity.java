package com.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(length = 20)
    private String phone;
    @Column(name = "is_active", nullable = false)
    private Boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @OneToMany(mappedBy = "user")
    @Builder.Default
    private Set<UserRoleEntity> userRoles = new HashSet<>();

    @OneToMany(mappedBy = "user")
    @Builder.Default
    private Set<CartEntity> carts = new HashSet<>();
    @OneToMany(mappedBy = "user")
    @Builder.Default
    private Set<OrderEntity> orders = new HashSet<>();
    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (active == null) {
            active = true;
        }
    }
    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

}
