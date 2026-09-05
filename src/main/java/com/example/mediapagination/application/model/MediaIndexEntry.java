package com.example.mediapagination.application.model;

import java.time.Instant;
import java.util.Objects;

public record MediaIndexEntry(long id, Instant publishTime) {
    public MediaIndexEntry {
        if (id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        Objects.requireNonNull(publishTime, "publishTime");
    }
}
