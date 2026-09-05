package com.example.mediapagination;

import com.example.mediapagination.application.command.DemoDataGenerator;
import com.example.mediapagination.application.model.GenerateMediaCommand;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.support.ContainerIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DemoDataGeneratorIT extends ContainerIntegrationTest {

    @Autowired private DemoDataGenerator generator;
    @Autowired private MediaQueryStore queryStore;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM media");
    }

    @Test
    void sameSeedProducesStableTitlesAndTimestampCollisions() {
        assertThat(generator.generate(
                new GenerateMediaCommand(1001L, 25, 10, 42L)))
                .isEqualTo(25L);

        List<Media> rows = queryStore.findPublishedPage(1001L, 0L, 25);
        assertThat(rows).hasSize(25);
        assertThat(rows).extracting(Media::title).first()
                .isEqualTo("Demo media 000024");
        assertThat(rows.stream().map(Media::publishTime).distinct().count())
                .isLessThan(25L);
    }
}
