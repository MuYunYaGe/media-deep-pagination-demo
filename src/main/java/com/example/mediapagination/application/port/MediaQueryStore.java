package com.example.mediapagination.application.port;

import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.domain.Media;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MediaQueryStore {

    List<Media> findPublishedPage(long categoryId, long offset, int size);

    long countPublished(long categoryId);

    List<Media> findPublishedByIds(List<Long> ids);

    Optional<Media> findById(long id);

    List<MediaIndexEntry> findPublishedIndexBatch(
            long categoryId, Instant beforeTime, Long beforeId, int limit);
}
