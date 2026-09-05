package com.example.mediapagination.application.model;

import java.time.Duration;
import java.util.Objects;

public record RebuildResult(Status status, long entryCount, Duration duration) {

    public RebuildResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(duration, "duration");
        if (entryCount < 0) {
            throw new IllegalArgumentException("entryCount must not be negative");
        }
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }

    public static RebuildResult skippedPresent(Duration duration) {
        return new RebuildResult(Status.SKIPPED_PRESENT, 0L, duration);
    }

    public static RebuildResult skippedLocked(Duration duration) {
        return new RebuildResult(Status.SKIPPED_LOCKED, 0L, duration);
    }

    public static RebuildResult skippedLocked() {
        return skippedLocked(Duration.ZERO);
    }

    public static RebuildResult empty(Duration duration) {
        return new RebuildResult(Status.EMPTY, 0L, duration);
    }

    public static RebuildResult rebuilt(long entryCount, Duration duration) {
        return new RebuildResult(Status.REBUILT, entryCount, duration);
    }

    public static RebuildResult failed(long entryCount, Duration duration) {
        return new RebuildResult(Status.FAILED, entryCount, duration);
    }

    public enum Status {
        SKIPPED_PRESENT,
        SKIPPED_LOCKED,
        EMPTY,
        REBUILT,
        FAILED
    }
}
