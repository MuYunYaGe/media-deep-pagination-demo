package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class OffsetPageHandler implements MediaPageHandler {

    private final MediaQueryStore store;
    private final PageWindow pageWindow;

    public OffsetPageHandler(MediaQueryStore store, PageWindow pageWindow) {
        this.store = Objects.requireNonNull(store, "store");
        this.pageWindow = Objects.requireNonNull(pageWindow, "pageWindow");
    }

    @Override
    public PageStrategy strategy() {
        return PageStrategy.OFFSET;
    }

    @Override
    public MediaPageResult query(MediaPageQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.strategy() != strategy()) {
            throw new IllegalArgumentException("handler requires strategy " + strategy());
        }

        long offset = pageWindow.offset(query.page(), query.size());
        List<Media> items = store.findPublishedPage(query.categoryId(), offset, query.size());
        long total = store.countPublished(query.categoryId());
        long totalPages = total / query.size() + (total % query.size() == 0 ? 0 : 1);

        return new MediaPageResult(query.categoryId(), query.page(), query.size(),
                total, totalPages, false, strategy(), CacheStatus.NOT_USED,
                false, items);
    }
}
