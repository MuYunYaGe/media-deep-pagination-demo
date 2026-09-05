package com.example.mediapagination.application.model;

import com.example.mediapagination.domain.Media;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record MediaChangedEvent(Media before, Media after) {

    public MediaChangedEvent {
        Objects.requireNonNull(after, "after");
        if (before != null && before.id() != after.id()) {
            throw new IllegalArgumentException("before and after media IDs must match");
        }
    }

    public Set<Long> affectedCategoryIds() {
        LinkedHashSet<Long> categories = new LinkedHashSet<>();
        if (before != null) {
            categories.add(before.categoryId());
        }
        categories.add(after.categoryId());
        return Collections.unmodifiableSet(categories);
    }
}
