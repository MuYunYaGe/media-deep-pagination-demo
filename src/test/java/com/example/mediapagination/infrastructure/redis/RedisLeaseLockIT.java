package com.example.mediapagination.infrastructure.redis;

import com.example.mediapagination.application.port.LeaseLock;
import com.example.mediapagination.application.port.Lease;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RedisLeaseLockIT extends ContainerIntegrationTest {

    private static final String KEY = "media:category:{8101}:publish:lock";

    @Autowired private LeaseLock lock;
    @Autowired private StringRedisTemplate redis;

    @AfterEach
    void cleanLock() {
        redis.delete(KEY);
    }

    @Test
    void aDifferentTokenCannotReleaseTheCurrentLease() {
        Lease first = lock.tryAcquire(KEY, Duration.ofSeconds(30)).orElseThrow();

        assertThat(lock.release(new Lease(first.key(), "wrong-token"))).isFalse();
        assertThat(lock.tryAcquire(KEY, Duration.ofSeconds(30))).isEmpty();
        assertThat(lock.release(first)).isTrue();
        assertThat(lock.tryAcquire(KEY, Duration.ofSeconds(30))).isPresent();
    }
}
