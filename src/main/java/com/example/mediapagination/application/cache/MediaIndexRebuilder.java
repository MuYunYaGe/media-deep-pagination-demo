package com.example.mediapagination.application.cache;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.port.Lease;
import com.example.mediapagination.application.port.LeaseLock;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.infrastructure.redis.MediaIndexKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds at most the configured bounded window under a fixed Redis lease.
 * The lease deliberately has no watchdog; deployments must size its TTL above
 * the observed rebuild budget or replace this demo lock with a managed lock.
 */
@Service
public class MediaIndexRebuilder {

    private static final Logger log = LoggerFactory.getLogger(MediaIndexRebuilder.class);

    private final MediaQueryStore mysql;
    private final MediaIndexStore index;
    private final LeaseLock lock;
    private final MediaIndexKeys keys;
    private final PaginationProperties properties;

    public MediaIndexRebuilder(
            MediaQueryStore mysql,
            MediaIndexStore index,
            LeaseLock lock,
            MediaIndexKeys keys,
            PaginationProperties properties) {
        this.mysql = Objects.requireNonNull(mysql, "mysql");
        this.index = Objects.requireNonNull(index, "index");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public RebuildResult rebuild(long categoryId, RebuildMode mode) {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        Objects.requireNonNull(mode, "mode");
        long started = System.nanoTime();

        try {
            if (mode == RebuildMode.IF_ABSENT
                    && index.state(categoryId) != IndexState.MISSING) {
                return RebuildResult.skippedPresent(elapsed(started));
            }
        } catch (RuntimeException failure) {
            log.warn("media index preflight failed categoryId={}", categoryId, failure);
            return RebuildResult.failed(0L, elapsed(started));
        }

        Optional<Lease> acquired;
        try {
            acquired = lock.tryAcquire(keys.lock(categoryId), properties.getLockTtl());
        } catch (RuntimeException failure) {
            log.warn("media index lease acquisition failed categoryId={}", categoryId, failure);
            return RebuildResult.failed(0L, elapsed(started));
        }
        if (acquired.isEmpty()) {
            return RebuildResult.skippedLocked(elapsed(started));
        }

        Lease lease = acquired.orElseThrow();
        String temporaryKey = null;
        boolean finalized = false;
        long entryCount = 0L;
        try {
            if (mode == RebuildMode.IF_ABSENT
                    && index.state(categoryId) != IndexState.MISSING) {
                return RebuildResult.skippedPresent(elapsed(started));
            }

            temporaryKey = index.temporaryKey(categoryId, UUID.randomUUID());
            MediaIndexEntry cursor = null;
            while (entryCount < properties.getWindowSize()) {
                int remaining = Math.toIntExact(properties.getWindowSize() - entryCount);
                int limit = Math.min(properties.getRebuildBatchSize(), remaining);
                List<MediaIndexEntry> batch = mysql.findPublishedIndexBatch(
                        categoryId,
                        cursor == null ? null : cursor.publishTime(),
                        cursor == null ? null : cursor.id(),
                        limit);
                if (batch.isEmpty()) {
                    break;
                }
                if (batch.size() > limit) {
                    throw new IllegalStateException("MySQL returned more index rows than requested");
                }
                index.append(temporaryKey, batch);
                entryCount += batch.size();
                cursor = batch.get(batch.size() - 1);
                if (batch.size() < limit) {
                    break;
                }
            }

            if (entryCount == 0L) {
                index.markEmpty(categoryId, properties.getEmptyMarkerTtl());
                finalized = true;
                return RebuildResult.empty(elapsed(started));
            }

            long actualCardinality = index.cardinality(temporaryKey);
            if (actualCardinality != entryCount) {
                log.warn("media index cardinality mismatch categoryId={} expected={} actual={}",
                        categoryId, entryCount, actualCardinality);
                return RebuildResult.failed(entryCount, elapsed(started));
            }

            index.expire(temporaryKey, jittered(properties.getIndexTtl()));
            index.replaceFormal(categoryId, temporaryKey);
            finalized = true;
            return RebuildResult.rebuilt(entryCount, elapsed(started));
        } catch (RuntimeException failure) {
            log.warn("media index rebuild failed categoryId={} entriesRead={}",
                    categoryId, entryCount, failure);
            return RebuildResult.failed(entryCount, elapsed(started));
        } finally {
            if (temporaryKey != null && !finalized) {
                safeDelete(temporaryKey, categoryId);
            }
            safeRelease(lease, categoryId);
        }
    }

    private Duration jittered(Duration configured) {
        long baseMillis = configured.toMillis();
        long maxJitter = baseMillis / 10L;
        long jitter = maxJitter == 0L
                ? 0L
                : ThreadLocalRandom.current().nextLong(maxJitter + 1L);
        return Duration.ofMillis(Math.addExact(baseMillis, jitter));
    }

    private void safeDelete(String temporaryKey, long categoryId) {
        try {
            index.delete(temporaryKey);
        } catch (RuntimeException cleanupFailure) {
            log.warn("temporary media index cleanup failed categoryId={}",
                    categoryId, cleanupFailure);
        }
    }

    private void safeRelease(Lease lease, long categoryId) {
        try {
            if (!lock.release(lease)) {
                log.warn("media index lease already expired categoryId={}", categoryId);
            }
        } catch (RuntimeException releaseFailure) {
            log.warn("media index lease release failed categoryId={}",
                    categoryId, releaseFailure);
        }
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0L, System.nanoTime() - started));
    }
}
