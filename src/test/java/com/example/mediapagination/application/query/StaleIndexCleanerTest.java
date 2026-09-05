package com.example.mediapagination.application.query;

import com.example.mediapagination.application.port.MediaIndexStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StaleIndexCleanerTest {

    @Mock
    private MediaIndexStore index;

    @Test
    void removesOnlyTheReportedStaleIds() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new StaleIndexCleaner(index, new PaginationMetrics(registry))
                .removeAsync(1001L, List.of(8L, 7L));

        verify(index).remove(1001L, List.of(8L, 7L));
        org.assertj.core.api.Assertions.assertThat(
                registry.get(PaginationMetrics.STALE_REMOVED)
                        .tag("outcome", "success").counter().count())
                .isEqualTo(2.0);
    }

    @Test
    void emptyCleanupRequestDoesNoRedisWork() {
        new StaleIndexCleaner(index,
                new PaginationMetrics(new SimpleMeterRegistry()))
                .removeAsync(1001L, List.of());

        verify(index, never()).remove(1001L, List.of());
    }
}
