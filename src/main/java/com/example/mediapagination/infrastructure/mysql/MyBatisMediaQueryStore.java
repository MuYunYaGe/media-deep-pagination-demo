package com.example.mediapagination.infrastructure.mysql;

import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MyBatisMediaQueryStore implements MediaQueryStore {

    private final MediaQueryMapper mapper;

    public MyBatisMediaQueryStore(MediaQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Media> findPublishedPage(long categoryId, long offset, int size) {
        return toDomain(mapper.findPublishedPage(categoryId, offset, size));
    }

    @Override
    public long countPublished(long categoryId) {
        return mapper.countPublished(categoryId);
    }

    @Override
    public List<Media> findPublishedByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return toDomain(mapper.findPublishedByIds(List.copyOf(ids)));
    }

    @Override
    public Optional<Media> findById(long id) {
        return mapper.findById(id).map(MediaRow::toDomain);
    }

    @Override
    public List<MediaIndexEntry> findPublishedIndexBatch(
            long categoryId, Instant beforeTime, Long beforeId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if ((beforeTime == null) != (beforeId == null)) {
            throw new IllegalArgumentException("cursor time and id must both be present or absent");
        }
        return List.copyOf(mapper.findPublishedIndexBatch(
                categoryId, beforeTime, beforeId, limit));
    }

    private static List<Media> toDomain(List<MediaRow> rows) {
        return rows.stream().map(MediaRow::toDomain).toList();
    }
}
