package com.example.mediapagination.api;

import com.example.mediapagination.domain.MediaStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull MediaStatus status) {
}
