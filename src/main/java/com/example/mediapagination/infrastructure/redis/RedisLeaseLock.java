package com.example.mediapagination.infrastructure.redis;

import com.example.mediapagination.application.port.Lease;
import com.example.mediapagination.application.port.LeaseLock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Repository
public class RedisLeaseLock implements LeaseLock {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> releaseScript;

    public RedisLeaseLock(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setLocation(new ClassPathResource("redis/release-lock.lua"));
        this.releaseScript.setResultType(Long.class);
    }

    @Override
    public Optional<Lease> tryAcquire(String key, Duration ttl) {
        requireKey(key);
        requirePositive(ttl);
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(
                key, token, ttl.toMillis(), TimeUnit.MILLISECONDS);
        return Boolean.TRUE.equals(acquired)
                ? Optional.of(new Lease(key, token))
                : Optional.empty();
    }

    @Override
    public boolean release(Lease lease) {
        Objects.requireNonNull(lease, "lease");
        Long released = redis.execute(
                releaseScript, List.of(lease.key()), lease.token());
        return Long.valueOf(1L).equals(released);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("lock key must not be blank");
        }
    }

    private static void requirePositive(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("lock ttl must be positive");
        }
    }
}
