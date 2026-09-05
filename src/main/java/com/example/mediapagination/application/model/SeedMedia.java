package com.example.mediapagination.application.model;

import com.example.mediapagination.domain.MediaStatus;

import java.time.Instant;
import java.util.Objects;

public record SeedMedia(
        long categoryId,
        String title,
        Instant publishTime,
        MediaStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public SeedMedia {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        if (title == null || title.isBlank() || title.length() > 200) {
            throw new IllegalArgumentException("title must contain 1 to 200 characters");
        }
        Objects.requireNonNull(publishTime, "publishTime");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
