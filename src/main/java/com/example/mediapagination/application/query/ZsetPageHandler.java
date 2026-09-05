package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.DeepPageUnavailableException;
import com.example.mediapagination.application.model.IndexLoadOutcome;
import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.RankRange;
import com.example.mediapagination.application.port.MediaIndexCoordinator;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.config.PaginationProperties;
import com.example.mediapagination.domain.Media;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class ZsetPageHandler implements MediaPageHandler {

    private final MediaIndexStore index;
    private final MediaQueryStore mysql;
    private final MediaIndexCoordinator coordinator;
    private final MediaOrderer orderer;
    private final PageWindow pageWindow;
    private final PaginationProperties properties;
    private final StaleIndexCleaner staleCleaner;
    private final OffsetPageHandler offsetHandler;
    private final PaginationMetrics metrics;

    public ZsetPageHandler(
            MediaIndexStore index,
            MediaQueryStore mysql,
            MediaIndexCoordinator coordinator,
            MediaOrderer orderer,
            PageWindow pageWindow,
            PaginationProperties properties,
            StaleIndexCleaner staleCleaner,
            OffsetPageHandler offsetHandler,
            PaginationMetrics metrics) {
        this.index = Objects.requireNonNull(index, "index");
        this.mysql = Objects.requireNonNull(mysql, "mysql");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.orderer = Objects.requireNonNull(orderer, "orderer");
        this.pageWindow = Objects.requireNonNull(pageWindow, "pageWindow");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.staleCleaner = Objects.requireNonNull(staleCleaner, "staleCleaner");
        this.offsetHandler = Objects.requireNonNull(offsetHandler, "offsetHandler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    @Override
    public PageStrategy strategy() {
        return PageStrategy.ZSET;
    }

    @Override
    public MediaPageResult query(MediaPageQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.strategy() != strategy()) {
            throw new IllegalArgumentException("handler requires strategy " + strategy());
        }

        RankRange ranks = pageWindow.ranks(query.page(), query.size());
        PaginationMetrics.PageSample sample = metrics.startPage(query, ranks.start());
        try {
            return sample.complete(queryIndex(query, ranks, sample));
        } catch (RuntimeException failure) {
            sample.fail(failure);
            throw failure;
        }
    }

    private MediaPageResult queryIndex(MediaPageQuery query, RankRange ranks,
                                       PaginationMetrics.PageSample sample) {
        IndexState state;
        try {
            state = sample.redis(() -> index.state(query.categoryId()));
        } catch (DataAccessException redisFailure) {
            return fallbackOrThrow(query, ranks.start(), sample);
        }

        CacheStatus cacheStatus = CacheStatus.HIT;
        if (state == IndexState.MISSING) {
            try {
                IndexLoadOutcome outcome = sample.redis(
                        () -> coordinator.ensureAvailable(query.categoryId()));
                if (outcome == IndexLoadOutcome.UNAVAILABLE) {
                    return fallbackOrThrow(query, ranks.start(), sample);
                }
                if (outcome == IndexLoadOutcome.EMPTY) {
                    return emptyResult(query);
                }
                state = IndexState.READY;
                cacheStatus = CacheStatus.MISS_REBUILT;
            } catch (DataAccessException redisFailure) {
                return fallbackOrThrow(query, ranks.start(), sample);
            }
        }
        if (state == IndexState.EMPTY) {
            return emptyResult(query);
        }

        IndexPage page;
        try {
            page = sample.redis(() -> new IndexPage(
                    index.reverseRange(query.categoryId(), ranks),
                    index.cardinality(query.categoryId())));
        } catch (DataAccessException redisFailure) {
            return fallbackOrThrow(query, ranks.start(), sample);
        }
        return assemble(query, page.orderedIds(), page.total(), cacheStatus, sample);
    }

    private MediaPageResult assemble(MediaPageQuery query, List<Long> orderedIds,
                                     long total, CacheStatus cacheStatus,
                                     PaginationMetrics.PageSample sample) {
        List<Media> unorderedDetails = orderedIds.isEmpty()
                ? List.of()
                : sample.database(() -> mysql.findPublishedByIds(orderedIds));
        List<Media> items = sample.reorder(
                () -> orderer.byIds(orderedIds, unorderedDetails));

        Set<Long> availableIds = new HashSet<>();
        unorderedDetails.forEach(media -> availableIds.add(media.id()));
        List<Long> staleIds = orderedIds.stream()
                .filter(id -> !availableIds.contains(id))
                .toList();
        if (!staleIds.isEmpty()) {
            staleCleaner.removeAsync(query.categoryId(), staleIds);
        }

        long totalPages = total / query.size() + (total % query.size() == 0 ? 0 : 1);
        return new MediaPageResult(query.categoryId(), query.page(), query.size(),
                total, totalPages, total >= properties.getWindowSize(),
                strategy(), cacheStatus, false, items);
    }

    private MediaPageResult emptyResult(MediaPageQuery query) {
        return new MediaPageResult(query.categoryId(), query.page(), query.size(),
                0L, 0L, false, strategy(), CacheStatus.EMPTY_MARKER,
                false, List.of());
    }

    private MediaPageResult fallbackOrThrow(MediaPageQuery query, long offset,
                                            PaginationMetrics.PageSample sample) {
        if (offset > properties.getShallowFallbackMaxOffset()) {
            throw new DeepPageUnavailableException();
        }
        MediaPageQuery mysqlQuery = new MediaPageQuery(query.categoryId(),
                PageStrategy.OFFSET, query.page(), query.size());
        return sample.database(() -> offsetHandler.queryWithoutMetrics(mysqlQuery))
                .asDegraded(CacheStatus.FALLBACK_MYSQL);
    }

    private record IndexPage(List<Long> orderedIds, long total) {
    }
}
