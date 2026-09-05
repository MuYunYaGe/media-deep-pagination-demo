package com.example.mediapagination.infrastructure.redis;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.model.RankRange;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class RedisMediaIndexStoreIT extends ContainerIntegrationTest {

    private static final long CATEGORY = 7101L;

    @Autowired
    private MediaIndexStore store;

    @Autowired
    private MediaIndexKeys keys;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void cleanKeys() {
        redis.delete(List.of(keys.formal(CATEGORY), keys.empty(CATEGORY)));
    }

    @Test
    void reverseRangeAndAtomicTrimKeepOnlyNewestMembers() {
        seedFormal(entry(1L, 1_000L), entry(2L, 2_000L));

        assertThat(store.addIfPresentAndTrim(CATEGORY, entry(3L, 3_000L), 2))
                .isTrue();
        assertThat(store.reverseRange(CATEGORY, new RankRange(0, 9)))
                .containsExactly(3L, 2L);
    }

    @Test
    void equalScoresUseDescendingNumericIdAsTheTieBreaker() {
        seedFormal(entry(9L, 1_000L), entry(10L, 1_000L), entry(2L, 1_000L));

        assertThat(store.reverseRange(CATEGORY, new RankRange(0, 9)))
                .containsExactly(10L, 9L, 2L);
    }

    @Test
    void incrementalAddMustNotCreateAnIncompleteFormalIndex() {
        assertThat(store.addIfPresentAndTrim(CATEGORY, entry(1L, 1_000L), 30_000))
                .isFalse();
        assertThat(store.state(CATEGORY)).isEqualTo(IndexState.MISSING);
    }

    @Test
    void renamePreservesTheTemporaryKeyTtl() {
        String temporary = store.temporaryKey(CATEGORY, UUID.randomUUID());
        store.append(temporary, List.of(entry(1L, 1_000L)));
        store.expire(temporary, Duration.ofSeconds(30));

        store.replaceFormal(CATEGORY, temporary);

        assertThat(store.state(CATEGORY)).isEqualTo(IndexState.READY);
        assertThat(redis.getExpire(keys.formal(CATEGORY), TimeUnit.SECONDS))
                .isBetween(1L, 30L);
    }

    @Test
    void emptyMarkerAtomicallyReplacesAnOldFormalIndex() {
        seedFormal(entry(1L, 1_000L));

        store.markEmpty(CATEGORY, Duration.ofSeconds(30));

        assertThat(store.state(CATEGORY)).isEqualTo(IndexState.EMPTY);
        assertThat(redis.hasKey(keys.formal(CATEGORY))).isFalse();
        assertThat(redis.opsForValue().get(keys.empty(CATEGORY))).isEqualTo("1");
    }

    @Test
    void replacementRejectsAKeyFromAnotherClusterSlot() {
        assertThatThrownBy(() -> store.replaceFormal(CATEGORY,
                keys.building(CATEGORY + 1, UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void seedFormal(MediaIndexEntry... entries) {
        String temporary = store.temporaryKey(CATEGORY, UUID.randomUUID());
        store.append(temporary, List.of(entries));
        store.expire(temporary, Duration.ofMinutes(1));
        store.replaceFormal(CATEGORY, temporary);
    }

    private static MediaIndexEntry entry(long id, long epochMillis) {
        return new MediaIndexEntry(id, Instant.ofEpochMilli(epochMillis));
    }
}
