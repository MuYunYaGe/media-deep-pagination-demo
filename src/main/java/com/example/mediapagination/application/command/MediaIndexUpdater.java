package com.example.mediapagination.application.command;

import com.example.mediapagination.application.cache.DirtyCategoryRegistry;
import com.example.mediapagination.application.model.MediaChangedEvent;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.port.MediaIndexCoordinator;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Objects;

@Service
public class MediaIndexUpdater {

    private static final Logger log = LoggerFactory.getLogger(MediaIndexUpdater.class);

    private final MediaIndexStore index;
    private final MediaIndexCoordinator coordinator;
    private final DirtyCategoryRegistry dirtyCategories;
    private final PaginationProperties properties;

    public MediaIndexUpdater(
            MediaIndexStore index,
            MediaIndexCoordinator coordinator,
            DirtyCategoryRegistry dirtyCategories,
            PaginationProperties properties) {
        this.index = Objects.requireNonNull(index, "index");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.dirtyCategories = Objects.requireNonNull(dirtyCategories, "dirtyCategories");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @Async("cacheMaintenanceExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMediaChanged(MediaChangedEvent event) {
        Objects.requireNonNull(event, "event");
        try {
            maintainIndex(event);
        } catch (RuntimeException failure) {
            event.affectedCategoryIds().forEach(dirtyCategories::mark);
            log.warn("media index maintenance failed; scheduled repair requested", failure);
        }
    }

    private void maintainIndex(MediaChangedEvent event) {
        Media before = event.before();
        Media after = event.after();
        boolean wasPublished = published(before);
        boolean isPublished = published(after);

        if (!wasPublished && isPublished) {
            addPublished(after, true);
            return;
        }
        if (wasPublished && !isPublished) {
            removeAndRefill(before);
            return;
        }
        if (!wasPublished) {
            return;
        }

        if (before.categoryId() != after.categoryId()) {
            index.remove(before.categoryId(), List.of(before.id()));
            index.clearEmptyMarker(after.categoryId());
            index.addIfPresentAndTrim(after.categoryId(), entry(after),
                    properties.getWindowSize());
            coordinator.requestRebuild(before.categoryId());
            coordinator.requestRebuild(after.categoryId());
            return;
        }
        if (!before.publishTime().equals(after.publishTime())) {
            addPublished(after, false);
        }
    }

    private void addPublished(Media media, boolean clearEmptyMarker) {
        if (clearEmptyMarker) {
            index.clearEmptyMarker(media.categoryId());
        }
        boolean updated = index.addIfPresentAndTrim(
                media.categoryId(), entry(media), properties.getWindowSize());
        if (!updated) {
            coordinator.requestRebuild(media.categoryId());
        }
    }

    private void removeAndRefill(Media media) {
        index.remove(media.categoryId(), List.of(media.id()));
        coordinator.requestRebuild(media.categoryId());
    }

    private static boolean published(Media media) {
        return media != null && media.status() == MediaStatus.PUBLISHED;
    }

    private static MediaIndexEntry entry(Media media) {
        return new MediaIndexEntry(media.id(), media.publishTime());
    }
}
