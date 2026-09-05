package com.example.mediapagination.api;

import com.example.mediapagination.application.model.GenerateMediaCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GenerateMediaRequest(
        @Min(1) long categoryId,
        @Min(1) @Max(100_000) int count,
        @Min(1) @Max(1_000) int batchSize,
        long seed
) {
    GenerateMediaCommand toCommand() {
        return new GenerateMediaCommand(categoryId, count, batchSize, seed);
    }
}
