package com.example.mediapagination.infrastructure.redis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaIdCodecTest {

    private final MediaIdCodec codec = new MediaIdCodec();

    @Test
    void equalScoresSortByDescendingNumericId() {
        assertThat(List.of(9L, 10L, 2L).stream()
                .map(codec::encode)
                .sorted(reverseOrder())
                .map(codec::decode))
                .containsExactly(10L, 9L, 2L);
    }

    @Test
    void encodingIsAlwaysTwentyCharacters() {
        assertThat(codec.encode(Long.MAX_VALUE)).hasSize(20);
        assertThat(codec.decode(codec.encode(Long.MAX_VALUE))).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsNonPositiveIdsAndMalformedMembers() {
        assertThatThrownBy(() -> codec.encode(0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("10"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("0000000000000000000x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allCategoryKeysUseTheSameClusterHashTag() {
        MediaIndexKeys keys = new MediaIndexKeys();

        assertThat(keys.formal(1001L)).isEqualTo("media:category:{1001}:publish");
        assertThat(keys.lock(1001L)).contains("{1001}");
        assertThat(keys.empty(1001L)).contains("{1001}");
        assertThat(keys.building(1001L, java.util.UUID.fromString(
                "00000000-0000-0000-0000-000000000001"))).contains("{1001}");
    }
}
