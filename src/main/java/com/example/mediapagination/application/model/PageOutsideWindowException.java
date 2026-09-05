package com.example.mediapagination.application.model;

public final class PageOutsideWindowException extends RuntimeException {
    public PageOutsideWindowException(long start, long end, int windowSize) {
        super("rank range " + start + ".." + end
                + " is outside the configured window of " + windowSize + " entries");
    }
}
