package com.repository;

import com.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, Long> {

    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

    // Revoke tất cả token của user (khi đổi mật khẩu, logout all devices)
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllByUserId(@Param("userId") Long userId);

    // Xóa token đã hết hạn hoặc đã revoke (cleanup job)
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.revoked = true OR r.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpiredAndRevoked();
}