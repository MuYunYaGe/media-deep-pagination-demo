package com.example.mediapagination.application.model;

public record ChangeMediaCategoryCommand(long id, long categoryId) {
    public ChangeMediaCategoryCommand {
        if (id <= 0) {
            throw new IllegalArgumentException("media id must be positive");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
    }
}
