package com.example.mediapagination.application.query;

import com.example.mediapagination.application.port.MediaIndexStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class StaleIndexCleaner {

    private static final Logger log = LoggerFactory.getLogger(StaleIndexCleaner.class);

    private final MediaIndexStore index;
    private final PaginationMetrics metrics;

    public StaleIndexCleaner(MediaIndexStore index, PaginationMetrics metrics) {
        this.index = Objects.requireNonNull(index, "index");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Async("cacheMaintenanceExecutor")
    public void removeAsync(long categoryId, List<Long> staleIds) {
        Objects.requireNonNull(staleIds, "staleIds");
        if (staleIds.isEmpty()) {
            return;
        }
        try {
            index.remove(categoryId, List.copyOf(staleIds));
            metrics.recordStaleRemoved(staleIds.size(), true);
            log.info("removed stale media index members categoryId={} count={}",
                    categoryId, staleIds.size());
        } catch (RuntimeException failure) {
            metrics.recordStaleRemoved(staleIds.size(), false);
            log.warn("failed to remove stale media index members categoryId={} count={}",
                    categoryId, staleIds.size(), failure);
        }
    }
}
