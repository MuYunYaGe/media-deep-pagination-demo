package com.example.mediapagination.infrastructure.mysql;

import com.example.mediapagination.application.model.MediaIndexEntry;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MyBatisMediaQueryStoreIT extends ContainerIntegrationTest {

    @Autowired
    private MediaQueryStore store;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTable() {
        jdbc.update("DELETE FROM media");
    }

    @Test
    void indexBatchUsesPublishTimeAndIdAsAStableCursor() {
        insert(11, 1001, "same-newer-id", "2026-09-05T10:00:00Z", "PUBLISHED");
        insert(10, 1001, "same-older-id", "2026-09-05T10:00:00Z", "PUBLISHED");
        insert(9, 1001, "older-time", "2026-09-05T09:00:00Z", "PUBLISHED");
        insert(12, 1001, "offline", "2026-09-05T11:00:00Z", "OFFLINE");

        List<MediaIndexEntry> first = store.findPublishedIndexBatch(1001, null, null, 1);
        List<MediaIndexEntry> second = store.findPublishedIndexBatch(
                1001, first.get(0).publishTime(), first.get(0).id(), 10);

        assertThat(first).extracting(MediaIndexEntry::id).containsExactly(11L);
        assertThat(second).extracting(MediaIndexEntry::id).containsExactly(10L, 9L);
    }

    @Test
    void offsetCountIdBatchAndSingleLookupUseTheSourceOfTruth() {
        insert(21, 1001, "new", "2026-09-05T11:00:00Z", "PUBLISHED");
        insert(20, 1001, "old", "2026-09-05T10:00:00Z", "PUBLISHED");
        insert(19, 1001, "hidden", "2026-09-05T12:00:00Z", "OFFLINE");
        insert(18, 1002, "other category", "2026-09-05T13:00:00Z", "PUBLISHED");

        assertThat(store.findPublishedPage(1001, 1, 1))
                .extracting(Media::id).containsExactly(20L);
        assertThat(store.countPublished(1001)).isEqualTo(2);
        assertThat(store.findPublishedByIds(List.of(20L, 19L, 21L)))
                .extracting(Media::id).containsExactlyInAnyOrder(20L, 21L);
        assertThat(store.findById(19L)).get().extracting(Media::status)
                .isEqualTo(com.example.mediapagination.domain.MediaStatus.OFFLINE);
        assertThat(store.findPublishedByIds(List.of())).isEmpty();
    }

    private void insert(long id, long categoryId, String title,
                        String publishTime, String status) {
        Instant instant = Instant.parse(publishTime);
        jdbc.update("""
                INSERT INTO media
                    (id, category_id, title, publish_time, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, categoryId, title, Timestamp.from(instant), status,
                Timestamp.from(instant), Timestamp.from(instant));
    }
}
