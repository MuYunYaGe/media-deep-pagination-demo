package com.example.mediapagination.infrastructure.mysql;

import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;

import java.time.Instant;

public class MediaRow {

    private long id;
    private long categoryId;
    private String title;
    private Instant publishTime;
    private MediaStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public Media toDomain() {
        return new Media(id, categoryId, title, publishTime, status, createdAt, updatedAt);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(long categoryId) {
        this.categoryId = categoryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Instant publishTime) {
        this.publishTime = publishTime;
    }

    public MediaStatus getStatus() {
        return status;
    }

    public void setStatus(MediaStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
