package com.example.mediapagination.api;

public record ApiError(String code, String message, String traceId) {
}
