package com.example.mediapagination.application.query;

import com.example.mediapagination.application.port.MediaIndexStore;
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
        new StaleIndexCleaner(index).removeAsync(1001L, List.of(8L, 7L));

        verify(index).remove(1001L, List.of(8L, 7L));
    }

    @Test
    void emptyCleanupRequestDoesNoRedisWork() {
        new StaleIndexCleaner(index).removeAsync(1001L, List.of());

        verify(index, never()).remove(1001L, List.of());
    }
}
