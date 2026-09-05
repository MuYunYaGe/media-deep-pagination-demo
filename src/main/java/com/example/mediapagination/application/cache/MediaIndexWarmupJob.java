package com.example.mediapagination.application.cache;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.port.Lease;
import com.example.mediapagination.application.port.LeaseLock;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.infrastructure.redis.MediaIndexKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Component
public class MediaIndexWarmupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaIndexWarmupJob.class);

    private final MediaIndexRebuilder rebuilder;
    private final MediaIndexStore index;
    private final LeaseLock lock;
    private final MediaIndexKeys keys;
    private final PaginationProperties properties;

    public MediaIndexWarmupJob(
            MediaIndexRebuilder rebuilder,
            MediaIndexStore index,
            LeaseLock lock,
            MediaIndexKeys keys,
            PaginationProperties properties) {
        this.rebuilder = Objects.requireNonNull(rebuilder, "rebuilder");
        this.index = Objects.requireNonNull(index, "index");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmAfterStartup() {
        if (properties.isWarmupEnabled()) {
            refreshConfiguredCategories();
        }
    }

    @Scheduled(fixedDelayString = "${demo.pagination.warmup-interval:PT5M}")
    public void scheduledRefresh() {
        if (!properties.isWarmupEnabled()) {
            return;
        }
        Optional<Lease> acquired;
        try {
            acquired = lock.tryAcquire(keys.warmupLock(), properties.getWarmupLockTtl());
        } catch (RuntimeException failure) {
            log.warn("media index warmup lease acquisition failed", failure);
            return;
        }
        if (acquired.isEmpty()) {
            return;
        }
        Lease lease = acquired.orElseThrow();
        try {
            refreshConfiguredCategories();
        } finally {
            try {
                lock.release(lease);
            } catch (RuntimeException failure) {
                log.warn("media index warmup lease release failed", failure);
            }
        }
    }

    public void refreshConfiguredCategories() {
        for (long categoryId : properties.getHotCategoryIds()) {
            try {
                IndexState state = index.state(categoryId);
                if (state == IndexState.MISSING
                        || (state == IndexState.READY && expiringSoon(categoryId))) {
                    rebuilder.rebuild(categoryId, RebuildMode.FORCE);
                }
            } catch (RuntimeException failure) {
                log.warn("configured media index warmup failed categoryId={}",
                        categoryId, failure);
            }
        }
    }

    private boolean expiringSoon(long categoryId) {
        Duration ttl = index.ttl(categoryId);
        return ttl.compareTo(properties.getRefreshBefore()) <= 0;
    }
}
