package com.service;

import com.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtil jwtUtil;

    @Value("${redis.token.blacklist-prefix:token:blacklist:}")
    private String blacklistPrefix;

    @Value("${redis.token.user-tokens-prefix:user:tokens:}")
    private String userTokensPrefix;

    /**
     * Thêm access token vào blacklist
     */
    public void blacklistAccessToken(String token, Long userId, Date expirationDate) {
        String jti = jwtUtil.extractJti(token);
        String key = blacklistPrefix + jti;

        // Tính TTL (thời gian sống) = thời gian token còn hiệu lực
        long ttl = expirationDate.getTime() - System.currentTimeMillis();

        if (ttl > 0) {
            redisTemplate.opsForValue().set(key, "revoked", ttl, TimeUnit.MILLISECONDS);
            log.info("Access token blacklisted - jti: {}, userId: {}, ttl: {} ms", jti, userId, ttl);
        }

        // Lưu mapping user -> tokens (để có thể revoke tất cả token của user sau)
        String userKey = userTokensPrefix + userId;
        redisTemplate.opsForSet().add(userKey, jti);
        redisTemplate.expire(userKey, ttl, TimeUnit.MILLISECONDS);
    }

    /**
     * Kiểm tra token có trong blacklist không
     */
    public boolean isBlacklisted(String token) {
        String jti = jwtUtil.extractJti(token);
        String key = blacklistPrefix + jti;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Revoke tất cả token của user (khi đổi mật khẩu, phát hiện xâm nhập)
     */
    public void revokeAllUserTokens(Long userId) {
        String userKey = userTokensPrefix + userId;
        Set<Object> tokenJtis = redisTemplate.opsForSet().members(userKey);

        if (tokenJtis != null && !tokenJtis.isEmpty()) {
            for (Object jtiObj : tokenJtis) {
                String jti = jtiObj.toString();
                String blacklistKey = blacklistPrefix + jti;
                redisTemplate.delete(blacklistKey);
            }
        }

        // Xóa set tokens của user
        redisTemplate.delete(userKey);
        log.info("All tokens revoked for userId: {}", userId);
    }

    /**
     * Cleanup expired tokens
     */
    public void cleanupExpiredTokens() {
        // Redis tự động xóa key khi hết TTL
        log.debug("Redis auto-cleanup handles expired tokens");
    }
}