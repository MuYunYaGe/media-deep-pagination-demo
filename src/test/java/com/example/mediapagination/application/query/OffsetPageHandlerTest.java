package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OffsetPageHandlerTest {

    @Mock
    private MediaQueryStore store;

    private OffsetPageHandler handler;
    private SimpleMeterRegistry metricsRegistry;

    @BeforeEach
    void setUp() {
        metricsRegistry = new SimpleMeterRegistry();
        handler = new OffsetPageHandler(store, new PageWindow(30_000),
                new PaginationMetrics(metricsRegistry));
    }

    @Test
    void offsetHandlerReturnsStablePageAndExactDatabaseTotal() {
        when(store.findPublishedPage(1001L, 10L, 10)).thenReturn(List.of(media(90L)));
        when(store.countPublished(1001L)).thenReturn(21L);

        MediaPageResult result = handler.query(
                new MediaPageQuery(1001L, PageStrategy.OFFSET, 2, 10));

        assertThat(result.total()).isEqualTo(21L);
        assertThat(result.totalPages()).isEqualTo(3L);
        assertThat(result.items()).extracting(Media::id).containsExactly(90L);
        assertThat(result.windowLimited()).isFalse();
        assertThat(result.cacheStatus()).isEqualTo(CacheStatus.NOT_USED);
        assertThat(result.degraded()).isFalse();
        verify(store).findPublishedPage(1001L, 10L, 10);
        assertThat(metricsRegistry.get(PaginationMetrics.PAGE_DURATION)
                .tag("strategy", "offset")
                .tag("cacheStatus", "not_used")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void emptyCategoryHasZeroPages() {
        when(store.findPublishedPage(1001L, 0L, 20)).thenReturn(List.of());
        when(store.countPublished(1001L)).thenReturn(0L);

        MediaPageResult result = handler.query(
                new MediaPageQuery(1001L, PageStrategy.OFFSET, 1, 20));

        assertThat(result.totalPages()).isZero();
        assertThat(result.items()).isEmpty();
    }

    @Test
    void rejectsQueriesOwnedByAnotherStrategy() {
        assertThatThrownBy(() -> handler.query(
                new MediaPageQuery(1001L, PageStrategy.ZSET, 1, 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OFFSET");
    }

    private static Media media(long id) {
        Instant now = Instant.parse("2026-09-05T10:00:00Z");
        return new Media(id, 1001L, "media-" + id, now,
                MediaStatus.PUBLISHED, now, now);
    }
}
