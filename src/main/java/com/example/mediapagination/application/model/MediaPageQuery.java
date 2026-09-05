package com.example.mediapagination.application.model;

import java.util.Objects;

public record MediaPageQuery(long categoryId, PageStrategy strategy, int page, int size) {
    public MediaPageQuery {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        Objects.requireNonNull(strategy, "strategy");
    }
}
