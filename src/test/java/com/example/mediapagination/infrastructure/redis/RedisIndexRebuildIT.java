package com.example.mediapagination.infrastructure.redis;

import com.example.mediapagination.application.cache.MediaIndexRebuilder;
import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.RankRange;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "demo.pagination.window-size=3",
        "demo.pagination.rebuild-batch-size=2"
})
@ActiveProfiles("test")
class RedisIndexRebuildIT extends ContainerIntegrationTest {

    private static final long CATEGORY = 8201L;

    @Autowired private MediaIndexRebuilder rebuilder;
    @Autowired private MediaIndexStore index;
    @Autowired private MediaIndexKeys keys;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM media");
        redis.delete(List.of(keys.formal(CATEGORY), keys.empty(CATEGORY),
                keys.lock(CATEGORY)));
    }

    @Test
    void forceRebuildCapsTheIndexAndKeepsTimestampTiesStable() {
        insert(1L, 1_000L);
        insert(2L, 2_000L);
        insert(3L, 3_000L);
        insert(4L, 3_000L);

        RebuildResult result = rebuilder.rebuild(CATEGORY, RebuildMode.FORCE);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.REBUILT);
        assertThat(result.entryCount()).isEqualTo(3L);
        assertThat(index.reverseRange(CATEGORY, new RankRange(0, 9)))
                .containsExactly(4L, 3L, 2L);
    }

    @Test
    void emptyCategoryRemovesAnOldIndexAndPublishesAnEmptyMarker() {
        String temporary = index.temporaryKey(CATEGORY, java.util.UUID.randomUUID());
        index.append(temporary, List.of(new com.example.mediapagination.application.model.MediaIndexEntry(
                99L, Instant.ofEpochMilli(99L))));
        index.expire(temporary, java.time.Duration.ofMinutes(1));
        index.replaceFormal(CATEGORY, temporary);

        RebuildResult result = rebuilder.rebuild(CATEGORY, RebuildMode.FORCE);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.EMPTY);
        assertThat(index.state(CATEGORY)).isEqualTo(IndexState.EMPTY);
    }

    private void insert(long id, long millis) {
        Instant instant = Instant.ofEpochMilli(millis);
        jdbc.update("""
                INSERT INTO media
                    (id, category_id, title, publish_time, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?)
                """, id, CATEGORY, "media-" + id, Timestamp.from(instant),
                Timestamp.from(instant), Timestamp.from(instant));
    }
}
