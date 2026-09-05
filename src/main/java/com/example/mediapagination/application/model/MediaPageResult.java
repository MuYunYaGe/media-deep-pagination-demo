package com.example.mediapagination.application.model;

import com.example.mediapagination.domain.Media;

import java.util.List;
import java.util.Objects;

public record MediaPageResult(
        long categoryId,
        int page,
        int size,
        long total,
        long totalPages,
        boolean windowLimited,
        PageStrategy strategy,
        CacheStatus cacheStatus,
        boolean degraded,
        List<Media> items
) {
    public MediaPageResult {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(cacheStatus, "cacheStatus");
        items = List.copyOf(items);
    }

    public MediaPageResult asDegraded(CacheStatus status) {
        return new MediaPageResult(categoryId, page, size, total, totalPages,
                windowLimited, strategy, status, true, items);
    }
}
