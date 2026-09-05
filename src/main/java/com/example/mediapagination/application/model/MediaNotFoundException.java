package com.example.mediapagination.application.model;

public final class MediaNotFoundException extends RuntimeException {

    private final long mediaId;

    public MediaNotFoundException(long mediaId) {
        super("media " + mediaId + " was not found");
        this.mediaId = mediaId;
    }

    public long mediaId() {
        return mediaId;
    }
}
