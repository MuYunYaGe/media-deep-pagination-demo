package com.example.mediapagination;

import com.example.mediapagination.application.cache.MediaIndexRebuilder;
import com.example.mediapagination.application.command.MediaCommandService;
import com.example.mediapagination.application.model.ChangeMediaCategoryCommand;
import com.example.mediapagination.application.model.ChangeMediaStatusCommand;
import com.example.mediapagination.application.model.CreateMediaCommand;
import com.example.mediapagination.application.model.RankRange;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import com.example.mediapagination.infrastructure.redis.MediaIndexKeys;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "demo.pagination.window-size=10",
        "demo.pagination.rebuild-batch-size=5"
})
@ActiveProfiles("test")
class MediaWriteLifecycleIT extends ContainerIntegrationTest {

    private static final long FIRST_CATEGORY = 8_301L;
    private static final long SECOND_CATEGORY = 8_302L;
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(5);

    @Autowired private MediaCommandService commands;
    @Autowired private MediaIndexRebuilder rebuilder;
    @Autowired private MediaIndexStore index;
    @Autowired private MediaQueryStore queries;
    @Autowired private MediaIndexKeys keys;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanAndBuildCompleteIndexes() {
        jdbc.update("DELETE FROM media");
        redis.delete(List.of(
                keys.formal(FIRST_CATEGORY), keys.empty(FIRST_CATEGORY),
                keys.lock(FIRST_CATEGORY), keys.formal(SECOND_CATEGORY),
                keys.empty(SECOND_CATEGORY), keys.lock(SECOND_CATEGORY)));

        insert(101L, FIRST_CATEGORY, "first", 1_000L);
        insert(201L, SECOND_CATEGORY, "second", 2_000L);
        assertThat(rebuilder.rebuild(FIRST_CATEGORY, RebuildMode.FORCE).status())
                .isEqualTo(RebuildResult.Status.REBUILT);
        assertThat(rebuilder.rebuild(SECOND_CATEGORY, RebuildMode.FORCE).status())
                .isEqualTo(RebuildResult.Status.REBUILT);
    }

    @Test
    void committedWritesEventuallyMaintainBothDatabaseAndRedisViews() {
        Media created = commands.create(new CreateMediaCommand(
                FIRST_CATEGORY, "newest", Instant.ofEpochMilli(3_000L),
                MediaStatus.PUBLISHED));

        await(() -> ids(FIRST_CATEGORY).equals(List.of(created.id(), 101L)));

        commands.changeStatus(new ChangeMediaStatusCommand(
                created.id(), MediaStatus.OFFLINE));
        assertThat(queries.findPublishedByIds(List.of(created.id()))).isEmpty();
        await(() -> !ids(FIRST_CATEGORY).contains(created.id()));

        commands.changeCategory(new ChangeMediaCategoryCommand(101L, SECOND_CATEGORY));
        await(() -> !ids(FIRST_CATEGORY).contains(101L)
                && ids(SECOND_CATEGORY).contains(101L));

        assertThat(queries.findById(101L)).get()
                .extracting(Media::categoryId)
                .isEqualTo(SECOND_CATEGORY);
    }

    private List<Long> ids(long categoryId) {
        return index.reverseRange(categoryId, new RankRange(0, 9));
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + ASYNC_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted while awaiting async index update", interrupted);
            }
        }
        assertThat(condition.getAsBoolean())
                .as("asynchronous index maintenance completed within %s", ASYNC_TIMEOUT)
                .isTrue();
    }

    private void insert(long id, long categoryId, String title, long publishMillis) {
        Instant instant = Instant.ofEpochMilli(publishMillis);
        jdbc.update("""
                INSERT INTO media
                    (id, category_id, title, publish_time, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PUBLISHED', ?, ?)
                """, id, categoryId, title, Timestamp.from(instant),
                Timestamp.from(instant), Timestamp.from(instant));
    }
}
