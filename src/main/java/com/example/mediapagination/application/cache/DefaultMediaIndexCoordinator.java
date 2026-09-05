package com.example.mediapagination.application.cache;

import com.example.mediapagination.application.model.IndexLoadOutcome;
import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.port.MediaIndexCoordinator;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.Sleeper;
import com.example.mediapagination.config.PaginationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

@Service
public class DefaultMediaIndexCoordinator implements MediaIndexCoordinator {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultMediaIndexCoordinator.class);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(50);

    private final MediaIndexRebuilder rebuilder;
    private final MediaIndexStore index;
    private final PaginationProperties properties;
    private final Sleeper sleeper;

    public DefaultMediaIndexCoordinator(
            MediaIndexRebuilder rebuilder,
            MediaIndexStore index,
            PaginationProperties properties,
            Sleeper sleeper) {
        this.rebuilder = Objects.requireNonNull(rebuilder, "rebuilder");
        this.index = Objects.requireNonNull(index, "index");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public IndexLoadOutcome ensureAvailable(long categoryId) {
        IndexLoadOutcome current = map(index.state(categoryId));
        if (current != IndexLoadOutcome.UNAVAILABLE) {
            return current;
        }

        RebuildResult result = rebuilder.rebuild(categoryId, RebuildMode.IF_ABSENT);
        return switch (result.status()) {
            case REBUILT -> IndexLoadOutcome.AVAILABLE;
            case EMPTY -> IndexLoadOutcome.EMPTY;
            case SKIPPED_PRESENT -> map(index.state(categoryId));
            case SKIPPED_LOCKED -> waitForLockWinner(categoryId);
            case FAILED -> IndexLoadOutcome.UNAVAILABLE;
        };
    }

    @Override
    @Async("cacheMaintenanceExecutor")
    public void requestRebuild(long categoryId) {
        RebuildResult result = rebuilder.rebuild(categoryId, RebuildMode.FORCE);
        log.info("asynchronous media index rebuild categoryId={} status={} entries={}",
                categoryId, result.status(), result.entryCount());
    }

    private IndexLoadOutcome waitForLockWinner(long categoryId) {
        Duration remaining = properties.getLockWait();
        while (remaining.compareTo(Duration.ZERO) > 0) {
            Duration pause = remaining.compareTo(POLL_INTERVAL) < 0
                    ? remaining : POLL_INTERVAL;
            try {
                sleeper.sleep(pause);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return IndexLoadOutcome.UNAVAILABLE;
            }
            IndexLoadOutcome outcome = map(index.state(categoryId));
            if (outcome != IndexLoadOutcome.UNAVAILABLE) {
                return outcome;
            }
            remaining = remaining.minus(pause);
        }
        return IndexLoadOutcome.UNAVAILABLE;
    }

    private static IndexLoadOutcome map(IndexState state) {
        return switch (state) {
            case READY -> IndexLoadOutcome.AVAILABLE;
            case EMPTY -> IndexLoadOutcome.EMPTY;
            case MISSING -> IndexLoadOutcome.UNAVAILABLE;
        };
    }
}
