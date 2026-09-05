package com.example.mediapagination.api;

import com.example.mediapagination.application.model.RebuildResult;

public record RebuildResponse(
        RebuildResult.Status status,
        long entryCount,
        long durationMs
) {
    public static RebuildResponse from(RebuildResult result) {
        return new RebuildResponse(
                result.status(), result.entryCount(), result.duration().toMillis());
    }
}
