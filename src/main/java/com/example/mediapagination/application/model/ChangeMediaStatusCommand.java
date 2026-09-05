package com.example.mediapagination.application.model;

import com.example.mediapagination.domain.MediaStatus;

import java.util.Objects;

public record ChangeMediaStatusCommand(long id, MediaStatus status) {
    public ChangeMediaStatusCommand {
        if (id <= 0) {
            throw new IllegalArgumentException("media id must be positive");
        }
        Objects.requireNonNull(status, "status");
    }
}
