package com.repository;

import com.entity.CartEntity;
import com.entity.enums.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<CartEntity, Long> {

    @Query("SELECT c FROM CartEntity c LEFT JOIN FETCH c.items i LEFT JOIN FETCH i.product WHERE c.user.id = :userId AND c.status = :status")
    Optional<CartEntity> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") CartStatus status);
}