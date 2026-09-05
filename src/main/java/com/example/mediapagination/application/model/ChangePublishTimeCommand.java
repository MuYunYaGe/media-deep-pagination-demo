package com.example.mediapagination.application.model;

import java.time.Instant;
import java.util.Objects;

public record ChangePublishTimeCommand(long id, Instant publishTime) {
    public ChangePublishTimeCommand {
        if (id <= 0) {
            throw new IllegalArgumentException("media id must be positive");
        }
        Objects.requireNonNull(publishTime, "publishTime");
    }
}
