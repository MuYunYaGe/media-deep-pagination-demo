package com.example.mediapagination.application.model;

public record GenerateMediaCommand(
        long categoryId,
        int count,
        int batchSize,
        long seed
) {
    public GenerateMediaCommand {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        if (count <= 0 || count > 100_000) {
            throw new IllegalArgumentException("count must be between 1 and 100000");
        }
        if (batchSize <= 0 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
    }
}
