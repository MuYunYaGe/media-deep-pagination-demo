package com.example.mediapagination.application.command;

import com.example.mediapagination.application.cache.DirtyCategoryRegistry;
import com.example.mediapagination.application.model.MediaChangedEvent;
import com.example.mediapagination.application.port.MediaIndexCoordinator;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaIndexUpdaterTest {

    @Mock private MediaIndexStore index;
    @Mock private MediaIndexCoordinator coordinator;
    @Mock private DirtyCategoryRegistry dirtyCategories;

    private MediaIndexUpdater updater;

    @BeforeEach
    void setUp() {
        updater = new MediaIndexUpdater(index, coordinator, dirtyCategories,
                new PaginationProperties());
    }

    @Test
    void publishingUpdatesOnlyAnAlreadyCompleteIndex() {
        MediaChangedEvent event = new MediaChangedEvent(
                media(10L, 1001L, MediaStatus.OFFLINE, 1_000L),
                media(10L, 1001L, MediaStatus.PUBLISHED, 1_000L));
        when(index.addIfPresentAndTrim(eq(1001L), any(), eq(30_000)))
                .thenReturn(false);

        updater.onMediaChanged(event);

        verify(index).clearEmptyMarker(1001L);
        verify(coordinator).requestRebuild(1001L);
    }

    @Test
    void categoryChangeRemovesOldMemberAndRecalibratesBothSides() {
        when(index.addIfPresentAndTrim(eq(1002L), any(), eq(30_000)))
                .thenReturn(true);

        updater.onMediaChanged(new MediaChangedEvent(
                media(10L, 1001L, MediaStatus.PUBLISHED, 1_000L),
                media(10L, 1002L, MediaStatus.PUBLISHED, 1_000L)));

        verify(index).remove(1001L, List.of(10L));
        verify(index).clearEmptyMarker(1002L);
        verify(coordinator).requestRebuild(1001L);
        verify(coordinator).requestRebuild(1002L);
    }

    @Test
    void takingMediaOfflineRemovesItAndRequestsAWindowRefill() {
        updater.onMediaChanged(new MediaChangedEvent(
                media(10L, 1001L, MediaStatus.PUBLISHED, 1_000L),
                media(10L, 1001L, MediaStatus.OFFLINE, 1_000L)));

        verify(index).remove(1001L, List.of(10L));
        verify(coordinator).requestRebuild(1001L);
    }

    @Test
    void titleOnlyChangeDoesNotTouchRedis() {
        Media before = media(10L, 1001L, MediaStatus.PUBLISHED, 1_000L);
        Media after = new Media(before.id(), before.categoryId(), "renamed",
                before.publishTime(), before.status(), before.createdAt(), before.updatedAt());

        updater.onMediaChanged(new MediaChangedEvent(before, after));

        verify(index, never()).remove(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection());
        verify(index, never()).addIfPresentAndTrim(
                org.mockito.ArgumentMatchers.anyLong(), any(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void redisFailureMarksEveryAffectedCategoryForRepair() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(index).remove(1001L, List.of(10L));
        MediaChangedEvent event = new MediaChangedEvent(
                media(10L, 1001L, MediaStatus.PUBLISHED, 1_000L),
                media(10L, 1002L, MediaStatus.PUBLISHED, 1_000L));

        updater.onMediaChanged(event);

        verify(dirtyCategories).mark(1001L);
        verify(dirtyCategories).mark(1002L);
    }

    private static Media media(long id, long categoryId, MediaStatus status,
                               long publishMillis) {
        Instant time = Instant.ofEpochMilli(publishMillis);
        return new Media(id, categoryId, "media-" + id, time,
                status, time, time);
    }
}
