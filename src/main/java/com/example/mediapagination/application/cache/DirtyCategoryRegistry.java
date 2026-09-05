package com.example.mediapagination.application.cache;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DirtyCategoryRegistry {

    private static final int DEFAULT_CAPACITY = 1_000;

    private final Set<Long> categories = ConcurrentHashMap.newKeySet();
    private final int capacity;

    public DirtyCategoryRegistry() {
        this(DEFAULT_CAPACITY);
    }

    DirtyCategoryRegistry(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public boolean mark(long categoryId) {
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId must be positive");
        }
        synchronized (categories) {
            if (categories.contains(categoryId)) {
                return true;
            }
            if (categories.size() >= capacity) {
                return false;
            }
            return categories.add(categoryId);
        }
    }

    public List<Long> drain(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        synchronized (categories) {
            List<Long> drained = new ArrayList<>(Math.min(limit, categories.size()));
            Iterator<Long> iterator = categories.iterator();
            while (iterator.hasNext() && drained.size() < limit) {
                Long categoryId = iterator.next();
                drained.add(categoryId);
                iterator.remove();
            }
            return List.copyOf(drained);
        }
    }

    public int size() {
        return categories.size();
    }
}
