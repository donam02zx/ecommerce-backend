package com.repository;

import com.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    @Query("SELECT u FROM UserEntity u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE u.email = :email")
    Optional<UserEntity> findByEmailWithRoles(@Param("email") String email);

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}