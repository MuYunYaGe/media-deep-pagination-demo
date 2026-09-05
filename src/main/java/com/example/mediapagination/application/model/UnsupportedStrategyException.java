package com.example.mediapagination.application.model;

public final class UnsupportedStrategyException extends RuntimeException {

    public UnsupportedStrategyException(String strategy) {
        super("unsupported pagination strategy: " + strategy);
    }
}
