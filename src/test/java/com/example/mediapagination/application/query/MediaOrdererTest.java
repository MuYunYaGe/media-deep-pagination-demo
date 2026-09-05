package com.example.mediapagination.application.query;

import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MediaOrdererTest {

    private final MediaOrderer orderer = new MediaOrderer();

    @Test
    void unorderedDatabaseRowsAreReturnedInRedisIdOrder() {
        List<Media> result = orderer.byIds(
                List.of(103L, 101L, 102L),
                List.of(media(102L), media(103L), media(101L)));

        assertThat(result).extracting(Media::id)
                .containsExactly(103L, 101L, 102L);
    }

    @Test
    void missingDatabaseRowsAreDroppedWithoutChangingSurvivorOrder() {
        List<Media> result = orderer.byIds(
                List.of(103L, 102L, 101L),
                List.of(media(101L), media(103L)));

        assertThat(result).extracting(Media::id)
                .containsExactly(103L, 101L);
    }

    private static Media media(long id) {
        Instant time = Instant.parse("2026-09-05T10:00:00Z");
        return new Media(id, 1001L, "media-" + id, time,
                MediaStatus.PUBLISHED, time, time);
    }
}
