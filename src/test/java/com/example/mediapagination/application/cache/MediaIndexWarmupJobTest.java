package com.example.mediapagination.application.cache;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.port.Lease;
import com.example.mediapagination.application.port.LeaseLock;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.infrastructure.redis.MediaIndexKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaIndexWarmupJobTest {

    @Mock private MediaIndexRebuilder rebuilder;
    @Mock private MediaIndexStore index;
    @Mock private LeaseLock lock;
    @Mock private DirtyCategoryRegistry dirtyCategories;

    private final MediaIndexKeys keys = new MediaIndexKeys();
    private PaginationProperties properties;
    private MediaIndexWarmupJob job;

    @BeforeEach
    void setUp() {
        properties = new PaginationProperties();
        properties.setHotCategoryIds(List.of(1001L, 1002L));
        job = new MediaIndexWarmupJob(
                rebuilder, index, lock, keys, properties, dirtyCategories);
    }

    @Test
    void refreshRebuildsOnlyMissingOrExpiringConfiguredCategories() {
        when(index.state(1001L)).thenReturn(IndexState.READY);
        when(index.state(1002L)).thenReturn(IndexState.READY);
        when(index.ttl(1001L)).thenReturn(Duration.ofMinutes(2));
        when(index.ttl(1002L)).thenReturn(Duration.ofMinutes(20));

        job.refreshConfiguredCategories();

        verify(rebuilder).rebuild(1001L, RebuildMode.FORCE);
        verify(rebuilder, never()).rebuild(1002L, RebuildMode.FORCE);
    }

    @Test
    void configuredMissingIndexIsPrewarmed() {
        properties.setHotCategoryIds(List.of(1001L));
        when(index.state(1001L)).thenReturn(IndexState.MISSING);

        job.refreshConfiguredCategories();

        verify(rebuilder).rebuild(1001L, RebuildMode.FORCE);
    }

    @Test
    void scheduledRefreshRequiresTheGlobalLease() {
        when(lock.tryAcquire(keys.warmupLock(), properties.getWarmupLockTtl()))
                .thenReturn(Optional.empty());

        job.scheduledRefresh();

        verify(index, never()).state(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void scheduledRefreshAlwaysReleasesTheGlobalLease() {
        Lease lease = new Lease(keys.warmupLock(), "job-token");
        properties.setHotCategoryIds(List.of());
        when(lock.tryAcquire(lease.key(), properties.getWarmupLockTtl()))
                .thenReturn(Optional.of(lease));

        job.scheduledRefresh();

        verify(lock).release(lease);
    }

    @Test
    void failedDirtyCategoryRepairIsRequeued() {
        when(dirtyCategories.drain(100)).thenReturn(List.of(1001L));
        when(rebuilder.rebuild(1001L, RebuildMode.FORCE))
                .thenReturn(com.example.mediapagination.application.model.RebuildResult
                        .failed(0L, Duration.ofMillis(1)));

        job.repairDirtyCategories();

        verify(dirtyCategories).mark(1001L);
    }
}
