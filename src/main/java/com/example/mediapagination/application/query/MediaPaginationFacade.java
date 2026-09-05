package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.UnsupportedStrategyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MediaPaginationFacade {

    private final Map<PageStrategy, MediaPageHandler> handlers;

    public MediaPaginationFacade(List<MediaPageHandler> handlers) {
        EnumMap<PageStrategy, MediaPageHandler> byStrategy =
                new EnumMap<>(PageStrategy.class);
        for (MediaPageHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            MediaPageHandler duplicate = byStrategy.put(handler.strategy(), handler);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "duplicate handler for strategy " + handler.strategy());
            }
        }
        this.handlers = Collections.unmodifiableMap(byStrategy);
    }

    public MediaPageResult query(MediaPageQuery query) {
        Objects.requireNonNull(query, "query");
        MediaPageHandler handler = handlers.get(query.strategy());
        if (handler == null) {
            throw new UnsupportedStrategyException(query.strategy().toString());
        }
        return handler.query(query);
    }
}
