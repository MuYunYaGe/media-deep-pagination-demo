package com.example.mediapagination.application.query;

import com.example.mediapagination.domain.Media;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MediaOrderer {

    public List<Media> byIds(List<Long> orderedIds, List<Media> unorderedDetails) {
        Objects.requireNonNull(orderedIds, "orderedIds");
        Objects.requireNonNull(unorderedDetails, "unorderedDetails");

        Map<Long, Media> byId = new HashMap<>(unorderedDetails.size());
        for (Media media : unorderedDetails) {
            byId.put(media.id(), media);
        }

        return orderedIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }
}
