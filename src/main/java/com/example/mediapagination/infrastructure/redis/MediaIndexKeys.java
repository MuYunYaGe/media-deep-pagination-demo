package com.example.mediapagination.infrastructure.redis;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component
public class MediaIndexKeys {

    public String formal(long categoryId) {
        requireCategory(categoryId);
        return "media:category:{" + categoryId + "}:publish";
    }

    public String lock(long categoryId) {
        return formal(categoryId) + ":lock";
    }

    public String building(long categoryId, UUID buildId) {
        Objects.requireNonNull(buildId, "buildId");
        return formal(categoryId) + ":building:" + buildId;
    }

    public String empty(long categoryId) {
        return formal(categoryId) + ":empty";
    }

    public String warmupLock() {
        return "media:warmup:{job}:lock";
    }

    private static void requireCategory(long categoryId) {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
    }
}
