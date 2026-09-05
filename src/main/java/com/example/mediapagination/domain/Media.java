package com.example.mediapagination.domain;

import java.time.Instant;
import java.util.Objects;

public record Media(
        long id,
        long categoryId,
        String title,
        Instant publishTime,
        MediaStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public Media {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(publishTime, "publishTime");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
