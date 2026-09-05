package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.RebuildResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

@Component
public class PaginationMetrics {

    public static final String PAGE_DURATION = "media.pagination.duration";
    public static final String REDIS_DURATION = "media.pagination.redis.duration";
    public static final String DB_DURATION = "media.pagination.db.duration";
    public static final String REORDER_DURATION = "media.pagination.reorder.duration";
    public static final String REBUILD_DURATION = "media.index.rebuild.duration";
    public static final String REBUILD_ENTRIES = "media.index.rebuild.entries";
    public static final String STALE_REMOVED = "media.index.stale.removed";
    public static final String DEGRADED = "media.pagination.degraded";

    private static final Logger log = LoggerFactory.getLogger(PaginationMetrics.class);

    private final MeterRegistry registry;

    public PaginationMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public PageSample startPage(MediaPageQuery query, long offset) {
        return new PageSample(Objects.requireNonNull(query, "query"), offset,
                Timer.start(registry));
    }

    public RebuildResult recordRebuild(RebuildResult result) {
        Objects.requireNonNull(result, "result");
        String outcome = lower(result.status());
        Timer.builder(REBUILD_DURATION)
                .tag("outcome", outcome)
                .register(registry)
                .record(result.duration());
        DistributionSummary.builder(REBUILD_ENTRIES)
                .tag("outcome", outcome)
                .register(registry)
                .record(result.entryCount());
        return result;
    }

    public void recordStaleRemoved(int count, boolean success) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        Counter.builder(STALE_REMOVED)
                .tag("outcome", success ? "success" : "failure")
                .register(registry)
                .increment(count);
    }

    public final class PageSample {

        private final MediaPageQuery query;
        private final long offset;
        private final Timer.Sample totalSample;
        private long redisNanos;
        private long dbNanos;
        private long reorderNanos;
        private boolean finished;

        private PageSample(MediaPageQuery query, long offset, Timer.Sample totalSample) {
            this.query = query;
            this.offset = offset;
            this.totalSample = totalSample;
        }

        public <T> T redis(Supplier<T> operation) {
            return time(REDIS_DURATION, Phase.REDIS, operation);
        }

        public <T> T database(Supplier<T> operation) {
            return time(DB_DURATION, Phase.DB, operation);
        }

        public <T> T reorder(Supplier<T> operation) {
            return time(REORDER_DURATION, Phase.REORDER, operation);
        }

        public MediaPageResult complete(MediaPageResult result) {
            Objects.requireNonNull(result, "result");
            ensureOpen();
            finished = true;
            String cacheStatus = lower(result.cacheStatus());
            long totalNanos = totalSample.stop(Timer.builder(PAGE_DURATION)
                    .tag("strategy", query.strategy().jsonValue())
                    .tag("cacheStatus", cacheStatus)
                    .tag("degraded", Boolean.toString(result.degraded()))
                    .tag("outcome", "success")
                    .register(registry));
            if (result.degraded()) {
                Counter.builder(DEGRADED)
                        .tag("strategy", query.strategy().jsonValue())
                        .tag("cacheStatus", cacheStatus)
                        .tag("degraded", "true")
                        .tag("outcome", "success")
                        .register(registry)
                        .increment();
            }
            logSummary(cacheStatus, result.degraded(), result.items().size(),
                    totalNanos, "success");
            return result;
        }

        public void fail(RuntimeException failure) {
            Objects.requireNonNull(failure, "failure");
            if (finished) {
                return;
            }
            finished = true;
            long totalNanos = totalSample.stop(Timer.builder(PAGE_DURATION)
                    .tag("strategy", query.strategy().jsonValue())
                    .tag("cacheStatus", "unavailable")
                    .tag("degraded", "false")
                    .tag("outcome", "failure")
                    .register(registry));
            logSummary("unavailable", false, 0, totalNanos, "failure");
        }

        private <T> T time(String metricName, Phase phase, Supplier<T> operation) {
            Objects.requireNonNull(operation, "operation");
            Timer.Sample sample = Timer.start(registry);
            try {
                T value = operation.get();
                add(phase, sample.stop(phaseTimer(metricName, "success")));
                return value;
            } catch (RuntimeException | Error failure) {
                add(phase, sample.stop(phaseTimer(metricName, "failure")));
                throw failure;
            }
        }

        private Timer phaseTimer(String metricName, String outcome) {
            return Timer.builder(metricName)
                    .tag("strategy", query.strategy().jsonValue())
                    .tag("outcome", outcome)
                    .register(registry);
        }

        private void add(Phase phase, long nanos) {
            switch (phase) {
                case REDIS -> redisNanos += nanos;
                case DB -> dbNanos += nanos;
                case REORDER -> reorderNanos += nanos;
            }
        }

        private void logSummary(String cacheStatus, boolean degraded,
                                int returnedCount, long totalNanos, String outcome) {
            log.info("pagination completed traceId={} categoryId={} strategy={} page={} "
                            + "size={} offset={} redisCostMs={} dbCostMs={} reorderCostMs={} "
                            + "totalCostMs={} cacheStatus={} degraded={} returnedCount={} outcome={}",
                    MDC.get("traceId"), query.categoryId(), query.strategy().jsonValue(),
                    query.page(), query.size(), offset, millis(redisNanos), millis(dbNanos),
                    millis(reorderNanos), millis(totalNanos), cacheStatus, degraded,
                    returnedCount, outcome);
        }

        private void ensureOpen() {
            if (finished) {
                throw new IllegalStateException("page sample already completed");
            }
        }
    }

    private enum Phase {
        REDIS,
        DB,
        REORDER
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static double millis(long nanos) {
        return Duration.ofNanos(nanos).toNanos() / 1_000_000.0;
    }
}
