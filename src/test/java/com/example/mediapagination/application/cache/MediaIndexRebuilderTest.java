package com.example.mediapagination.application.cache;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.port.LeaseLock;
import com.example.mediapagination.application.port.Lease;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.infrastructure.redis.MediaIndexKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaIndexRebuilderTest {

    @Mock private MediaQueryStore mysql;
    @Mock private MediaIndexStore index;
    @Mock private LeaseLock lock;

    private final MediaIndexKeys keys = new MediaIndexKeys();
    private PaginationProperties properties;
    private MediaIndexRebuilder rebuilder;

    @BeforeEach
    void setUp() {
        properties = new PaginationProperties();
        properties.setWindowSize(3);
        properties.setRebuildBatchSize(2);
        properties.setIndexTtl(Duration.ofMinutes(30));
        rebuilder = new MediaIndexRebuilder(mysql, index, lock, keys, properties);
    }

    @Test
    void ifAbsentSkipsAnAlreadyCompleteIndexWithoutTakingTheLock() {
        when(index.state(1001L)).thenReturn(IndexState.READY);

        RebuildResult result = rebuilder.rebuild(1001L, RebuildMode.IF_ABSENT);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.SKIPPED_PRESENT);
        verify(lock, never()).tryAcquire(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void lockLoserReturnsImmediatelyWithoutReadingMysql() {
        when(index.state(1001L)).thenReturn(IndexState.MISSING);
        when(lock.tryAcquire(keys.lock(1001L), properties.getLockTtl()))
                .thenReturn(Optional.empty());

        RebuildResult result = rebuilder.rebuild(1001L, RebuildMode.IF_ABSENT);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.SKIPPED_LOCKED);
        verify(mysql, never()).findPublishedIndexBatch(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void rebuildUsesCompositeCursorAndAtomicallyPublishesValidatedIndex() {
        Lease lease = new Lease(keys.lock(1001L), "token-1");
        when(lock.tryAcquire(lease.key(), properties.getLockTtl()))
                .thenReturn(Optional.of(lease));
        when(index.temporaryKey(org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any())).thenReturn("temp-key");
        when(mysql.findPublishedIndexBatch(1001L, null, null, 2))
                .thenReturn(List.of(entry(5L, 5_000L), entry(4L, 4_000L)));
        when(mysql.findPublishedIndexBatch(1001L, instant(4_000L), 4L, 1))
                .thenReturn(List.of(entry(3L, 3_000L)));
        when(index.cardinality("temp-key")).thenReturn(3L);

        RebuildResult result = rebuilder.rebuild(1001L, RebuildMode.FORCE);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.REBUILT);
        assertThat(result.entryCount()).isEqualTo(3L);
        InOrder order = inOrder(index);
        order.verify(index).append("temp-key",
                List.of(entry(5L, 5_000L), entry(4L, 4_000L)));
        order.verify(index).append("temp-key", List.of(entry(3L, 3_000L)));
        order.verify(index).cardinality("temp-key");
        order.verify(index).expire(org.mockito.ArgumentMatchers.eq("temp-key"),
                org.mockito.ArgumentMatchers.any());
        order.verify(index).replaceFormal(1001L, "temp-key");
        verify(lock).release(lease);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(index).expire(org.mockito.ArgumentMatchers.eq("temp-key"), ttl.capture());
        assertThat(ttl.getValue()).isBetween(
                Duration.ofMinutes(30), Duration.ofMinutes(33));
    }

    @Test
    void cardinalityMismatchDeletesOnlyTheTemporaryIndex() {
        Lease lease = new Lease(keys.lock(1001L), "token-1");
        when(lock.tryAcquire(lease.key(), properties.getLockTtl()))
                .thenReturn(Optional.of(lease));
        when(index.temporaryKey(org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any())).thenReturn("temp-key");
        when(mysql.findPublishedIndexBatch(1001L, null, null, 2))
                .thenReturn(List.of(entry(2L, 2_000L), entry(1L, 1_000L)));
        when(mysql.findPublishedIndexBatch(1001L, instant(1_000L), 1L, 1))
                .thenReturn(List.of());
        when(index.cardinality("temp-key")).thenReturn(1L);

        RebuildResult result = rebuilder.rebuild(1001L, RebuildMode.FORCE);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.FAILED);
        verify(index).delete("temp-key");
        verify(index, never()).replaceFormal(
                org.mockito.ArgumentMatchers.anyLong(), anyString());
        verify(index, never()).markEmpty(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void emptySourceAtomicallyPublishesTheEmptyMarker() {
        Lease lease = new Lease(keys.lock(1001L), "token-1");
        when(lock.tryAcquire(lease.key(), properties.getLockTtl()))
                .thenReturn(Optional.of(lease));
        when(index.temporaryKey(org.mockito.ArgumentMatchers.eq(1001L),
                org.mockito.ArgumentMatchers.any())).thenReturn("temp-key");
        when(mysql.findPublishedIndexBatch(1001L, null, null, 2))
                .thenReturn(List.of());

        RebuildResult result = rebuilder.rebuild(1001L, RebuildMode.FORCE);

        assertThat(result.status()).isEqualTo(RebuildResult.Status.EMPTY);
        verify(index).markEmpty(1001L, properties.getEmptyMarkerTtl());
        verify(index, never()).replaceFormal(
                org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    private static MediaIndexEntry entry(long id, long millis) {
        return new MediaIndexEntry(id, instant(millis));
    }

    private static Instant instant(long millis) {
        return Instant.ofEpochMilli(millis);
    }
}
