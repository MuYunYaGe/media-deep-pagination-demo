package com.example.mediapagination.application.model;

public enum CacheStatus {
    NOT_USED,
    HIT,
    MISS_REBUILT,
    EMPTY_MARKER,
    FALLBACK_MYSQL
}
