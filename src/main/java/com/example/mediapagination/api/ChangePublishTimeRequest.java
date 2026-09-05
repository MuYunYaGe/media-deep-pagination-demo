package com.example.mediapagination.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ChangePublishTimeRequest(@NotNull Instant publishTime) {
}
