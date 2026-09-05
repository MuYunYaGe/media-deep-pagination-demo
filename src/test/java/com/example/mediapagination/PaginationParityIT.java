package com.example.mediapagination;

import com.example.mediapagination.application.cache.MediaIndexRebuilder;
import com.example.mediapagination.application.command.DemoDataGenerator;
import com.example.mediapagination.application.model.GenerateMediaCommand;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageOutsideWindowException;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.query.MediaPaginationFacade;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.infrastructure.redis.MediaIndexKeys;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PaginationParityIT extends ContainerIntegrationTest {

    private static final long CATEGORY = 1001L;

    @Autowired private DemoDataGenerator generator;
    @Autowired private MediaIndexRebuilder rebuilder;
    @Autowired private MediaPaginationFacade facade;
    @Autowired private MediaIndexKeys keys;
    @Autowired private StringRedisTemplate redis;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM media");
        redis.delete(List.of(keys.formal(CATEGORY), keys.empty(CATEGORY),
                keys.lock(CATEGORY)));
    }

    @Test
    void offsetAndZsetReturnTheSameIdsForPageThreeThousand() {
        generator.generate(new GenerateMediaCommand(CATEGORY, 30_050, 500, 42L));
        assertThat(rebuilder.rebuild(CATEGORY, RebuildMode.FORCE).status())
                .isEqualTo(RebuildResult.Status.REBUILT);

        MediaPageResult offset = facade.query(new MediaPageQuery(
                CATEGORY, PageStrategy.OFFSET, 3_000, 10));
        MediaPageResult zset = facade.query(new MediaPageQuery(
                CATEGORY, PageStrategy.ZSET, 3_000, 10));

        assertThat(zset.items()).extracting(Media::id)
                .containsExactlyElementsOf(
                        offset.items().stream().map(Media::id).toList());
        assertThat(zset.total()).isEqualTo(30_000L);
        assertThat(zset.windowLimited()).isTrue();
        assertThatThrownBy(() -> facade.query(new MediaPageQuery(
                CATEGORY, PageStrategy.ZSET, 3_001, 10)))
                .isInstanceOf(PageOutsideWindowException.class);
    }
}
