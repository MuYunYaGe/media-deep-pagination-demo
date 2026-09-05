package com.example.mediapagination.application.port;

import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.model.RankRange;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MediaIndexStore {

    IndexState state(long categoryId);

    List<Long> reverseRange(long categoryId, RankRange ranks);

    long cardinality(long categoryId);

    boolean addIfPresentAndTrim(long categoryId, MediaIndexEntry entry, int maxEntries);

    void remove(long categoryId, Collection<Long> ids);

    void clearEmptyMarker(long categoryId);

    String temporaryKey(long categoryId, UUID buildId);

    void append(String key, List<MediaIndexEntry> entries);

    long cardinality(String key);

    void expire(String key, Duration ttl);

    void replaceFormal(long categoryId, String temporaryKey);

    void delete(String key);

    void markEmpty(long categoryId, Duration ttl);

    Duration ttl(long categoryId);
}
