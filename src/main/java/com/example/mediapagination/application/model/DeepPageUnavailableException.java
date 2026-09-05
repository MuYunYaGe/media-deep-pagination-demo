package com.example.mediapagination.application.model;

public final class DeepPageUnavailableException extends RuntimeException {

    public DeepPageUnavailableException() {
        super("Redis index is unavailable and this page is too deep for MySQL fallback");
    }
}
