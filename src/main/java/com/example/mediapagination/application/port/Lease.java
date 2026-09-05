package com.example.mediapagination.application.port;

public record Lease(String key, String token) {

    public Lease {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("lease key must not be blank");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("lease token must not be blank");
        }
    }
}
