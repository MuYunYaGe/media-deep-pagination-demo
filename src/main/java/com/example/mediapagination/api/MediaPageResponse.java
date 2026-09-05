package com.example.mediapagination.api;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.domain.Media;

import java.util.List;

public record MediaPageResponse(
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
    public MediaPageResponse {
        items = List.copyOf(items);
    }

    public static MediaPageResponse from(MediaPageResult result) {
        return new MediaPageResponse(result.categoryId(), result.page(), result.size(),
                result.total(), result.totalPages(), result.windowLimited(),
                result.strategy(), result.cacheStatus(), result.degraded(), result.items());
    }
}
