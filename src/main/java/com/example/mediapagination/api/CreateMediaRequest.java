package com.example.mediapagination.api;

import com.example.mediapagination.application.model.CreateMediaCommand;
import com.example.mediapagination.domain.MediaStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateMediaRequest(
        @Min(1) long categoryId,
        @NotBlank @Size(max = 200) String title,
        @NotNull Instant publishTime,
        @NotNull MediaStatus status
) {
    CreateMediaCommand toCommand() {
        return new CreateMediaCommand(categoryId, title, publishTime, status);
    }
}
