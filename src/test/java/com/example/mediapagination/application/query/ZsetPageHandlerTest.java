package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.DeepPageUnavailableException;
import com.example.mediapagination.application.model.IndexLoadOutcome;
import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.RankRange;
import com.example.mediapagination.application.port.MediaIndexCoordinator;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZsetPageHandlerTest {

    @Mock private MediaIndexStore index;
    @Mock private MediaQueryStore mysql;
    @Mock private MediaIndexCoordinator coordinator;
    @Mock private StaleIndexCleaner cleaner;
    @Mock private OffsetPageHandler offset;

    private ZsetPageHandler handler;
    private SimpleMeterRegistry metricsRegistry;

    @BeforeEach
    void setUp() {
        PaginationProperties properties = new PaginationProperties();
        metricsRegistry = new SimpleMeterRegistry();
        handler = new ZsetPageHandler(index, mysql, coordinator,
                new MediaOrderer(), new PageWindow(properties.getWindowSize()),
                properties, cleaner, offset, new PaginationMetrics(metricsRegistry));
    }

    @Test
    void hitFetchesDetailsOnceAndRestoresRedisOrder() {
        when(index.state(1001L)).thenReturn(IndexState.READY);
        when(index.reverseRange(1001L, new RankRange(10, 19)))
                .thenReturn(List.of(9L, 7L, 8L));
        when(index.cardinality(1001L)).thenReturn(30_000L);
        when(mysql.findPublishedByIds(List.of(9L, 7L, 8L)))
                .thenReturn(List.of(media(8L), media(9L), media(7L)));

        MediaPageResult result = handler.query(query(2));

        assertThat(result.items()).extracting(Media::id).containsExactly(9L, 7L, 8L);
        assertThat(result.total()).isEqualTo(30_000L);
        assertThat(result.totalPages()).isEqualTo(3_000L);
        assertThat(result.windowLimited()).isTrue();
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.HIT);
        verify(cleaner, never()).removeAsync(anyLong(), anyList());
        assertThat(metricsRegistry.get(PaginationMetrics.REDIS_DURATION)
                .tag("strategy", "zset").timer().count()).isPositive();
        assertThat(metricsRegistry.get(PaginationMetrics.DB_DURATION)
                .tag("strategy", "zset").timer().count()).isEqualTo(1L);
        assertThat(metricsRegistry.get(PaginationMetrics.REORDER_DURATION)
                .tag("strategy", "zset").timer().count()).isEqualTo(1L);
    }

    @Test
    void staleIdMakesCurrentPageShortAndSchedulesCleanup() {
        when(index.state(1001L)).thenReturn(IndexState.READY);
        when(index.reverseRange(1001L, new RankRange(0, 9)))
                .thenReturn(List.of(9L, 8L));
        when(index.cardinality(1001L)).thenReturn(2L);
        when(mysql.findPublishedByIds(List.of(9L, 8L))).thenReturn(List.of(media(9L)));

        MediaPageResult result = handler.query(query(1));

        assertThat(result.items()).extracting(Media::id).containsExactly(9L);
        verify(cleaner).removeAsync(1001L, List.of(8L));
    }

    @Test
    void emptyMarkerAvoidsMysqlAndReturnsAnEmptyPage() {
        when(index.state(1001L)).thenReturn(IndexState.EMPTY);

        MediaPageResult result = handler.query(query(1));

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.EMPTY_MARKER);
        verify(mysql, never()).findPublishedByIds(anyList());
    }

    @Test
    void rebuiltMissIsReportedSeparatelyFromAHit() {
        when(index.state(1001L)).thenReturn(IndexState.MISSING);
        when(coordinator.ensureAvailable(1001L)).thenReturn(IndexLoadOutcome.AVAILABLE);
        when(index.reverseRange(1001L, new RankRange(0, 9))).thenReturn(List.of());
        when(index.cardinality(1001L)).thenReturn(0L);

        assertThat(handler.query(query(1)).cacheStatus())
                .isEqualTo(CacheStatus.MISS_REBUILT);
    }

    @Test
    void redisFailureFallsBackOnlyForShallowOffset() {
        when(index.state(anyLong()))
                .thenThrow(new RedisConnectionFailureException("down"));
        MediaPageQuery mysqlQuery = new MediaPageQuery(
                1001L, PageStrategy.OFFSET, 2, 10);
        MediaPageResult mysqlResult = new MediaPageResult(
                1001L, 2, 10, 21L, 3L, false, PageStrategy.OFFSET,
                CacheStatus.NOT_USED, false, List.of(media(9L)));
        when(offset.query(mysqlQuery)).thenReturn(mysqlResult);

        MediaPageResult result = handler.query(query(2));

        assertThat(result.degraded()).isTrue();
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.FALLBACK_MYSQL);
        assertThatThrownBy(() -> handler.query(query(102)))
                .isInstanceOf(DeepPageUnavailableException.class);
    }

    @Test
    void unavailableLazyLoadUsesTheSameBoundedFallbackRule() {
        when(index.state(1001L)).thenReturn(IndexState.MISSING);
        when(coordinator.ensureAvailable(1001L)).thenReturn(IndexLoadOutcome.UNAVAILABLE);

        assertThatThrownBy(() -> handler.query(query(102)))
                .isInstanceOf(DeepPageUnavailableException.class);
    }

    @Test
    void mysqlDetailFailureIsNotMisclassifiedAsARedisFailure() {
        when(index.state(1001L)).thenReturn(IndexState.READY);
        when(index.reverseRange(1001L, new RankRange(0, 9))).thenReturn(List.of(9L));
        when(index.cardinality(1001L)).thenReturn(1L);
        DataAccessResourceFailureException databaseFailure =
                new DataAccessResourceFailureException("mysql down");
        when(mysql.findPublishedByIds(List.of(9L))).thenThrow(databaseFailure);

        assertThatThrownBy(() -> handler.query(query(1))).isSameAs(databaseFailure);
        verify(offset, never()).query(org.mockito.ArgumentMatchers.any());
    }

    private static MediaPageQuery query(int page) {
        return new MediaPageQuery(1001L, PageStrategy.ZSET, page, 10);
    }

    private static Media media(long id) {
        Instant now = Instant.parse("2026-09-05T10:00:00Z");
        return new Media(id, 1001L, "media-" + id, now,
                MediaStatus.PUBLISHED, now, now);
    }
}
