package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.PageOutsideWindowException;
import com.example.mediapagination.application.model.RankRange;

public final class PageWindow {

    private static final int MAX_PAGE_SIZE = 100;

    private final int maxEntries;

    public PageWindow(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
    }

    public long offset(int page, int size) {
        validate(page, size);
        return Math.multiplyExact((long) page - 1L, size);
    }

    public RankRange ranks(int page, int size) {
        long start = offset(page, size);
        long end = Math.addExact(start, size - 1L);
        if (end >= maxEntries) {
            throw new PageOutsideWindowException(start, end, maxEntries);
        }
        return new RankRange(start, end);
    }

    private static void validate(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
    }
}
