package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.RebuildResult;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationMetricsTest {

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry registry =
            new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private PaginationMetrics metrics;

    @BeforeEach
    void setUp() {
        registry.clear();
        metrics = new PaginationMetrics(registry);
    }

    @Test
    void pageSamplePublishesPhaseAndCompletionMetricsWithLowCardinalityTags() {
        PaginationMetrics.PageSample sample = metrics.startPage(
                new MediaPageQuery(1001L, PageStrategy.ZSET, 3_000, 10), 29_990L);
        sample.redis(() -> {
            clock.add(Duration.ofMillis(2));
            return List.of(9L, 8L);
        });
        sample.database(() -> {
            clock.add(Duration.ofMillis(3));
            return List.of();
        });
        sample.reorder(() -> {
            clock.add(Duration.ofMillis(1));
            return List.of();
        });

        sample.complete(new MediaPageResult(
                1001L, 3_000, 10, 30_000L, 3_000L, true,
                PageStrategy.ZSET, CacheStatus.HIT, false, List.of()));

        assertThat(registry.get("media.pagination.redis.duration")
                .tag("strategy", "zset").tag("outcome", "success")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get("media.pagination.db.duration")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get("media.pagination.reorder.duration")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.get("media.pagination.duration")
                .tag("cacheStatus", "hit").tag("degraded", "false")
                .timer().count()).isEqualTo(1L);

        Set<String> allowedTagKeys = Set.of(
                "strategy", "cacheStatus", "outcome", "degraded");
        assertThat(registry.getMeters())
                .flatExtracting(meter -> meter.getId().getTags())
                .extracting(io.micrometer.core.instrument.Tag::getKey)
                .allMatch(allowedTagKeys::contains);
    }

    @Test
    void degradedPagesAndMaintenanceOperationsUseDedicatedMeters() {
        PaginationMetrics.PageSample sample = metrics.startPage(
                new MediaPageQuery(1001L, PageStrategy.ZSET, 2, 10), 10L);
        sample.complete(new MediaPageResult(
                1001L, 2, 10, 21L, 3L, false,
                PageStrategy.OFFSET, CacheStatus.FALLBACK_MYSQL,
                true, List.of()));
        metrics.recordRebuild(RebuildResult.rebuilt(
                20L, Duration.ofMillis(7)));
        metrics.recordStaleRemoved(3, true);

        assertThat(registry.get("media.pagination.degraded")
                .tag("strategy", "zset").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("media.index.rebuild.duration")
                .tag("outcome", "rebuilt").timer().count()).isEqualTo(1L);
        assertThat(registry.get("media.index.rebuild.entries")
                .tag("outcome", "rebuilt").summary().totalAmount())
                .isEqualTo(20.0);
        assertThat(registry.get("media.index.stale.removed")
                .tag("outcome", "success").counter().count()).isEqualTo(3.0);
    }
}
