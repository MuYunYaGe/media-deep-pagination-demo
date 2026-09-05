package com.example.mediapagination.application.model;

public record RankRange(long start, long end) {
    public RankRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("invalid rank range");
        }
    }
}
