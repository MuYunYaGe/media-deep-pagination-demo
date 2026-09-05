package com.example.mediapagination.application.cache;

import com.example.mediapagination.application.model.IndexLoadOutcome;
import com.example.mediapagination.application.model.IndexState;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import com.example.mediapagination.application.port.MediaIndexStore;
import com.example.mediapagination.application.port.Sleeper;
import com.example.mediapagination.config.PaginationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultMediaIndexCoordinatorTest {

    @Mock private MediaIndexRebuilder rebuilder;
    @Mock private MediaIndexStore index;
    @Mock private Sleeper sleeper;

    private DefaultMediaIndexCoordinator coordinator;

    @BeforeEach
    void setUp() {
        PaginationProperties properties = new PaginationProperties();
        coordinator = new DefaultMediaIndexCoordinator(
                rebuilder, index, properties, sleeper);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void existingReadyIndexDoesNotStartARebuild() {
        when(index.state(1001L)).thenReturn(IndexState.READY);

        assertThat(coordinator.ensureAvailable(1001L))
                .isEqualTo(IndexLoadOutcome.AVAILABLE);
        verify(rebuilder, never()).rebuild(1001L, RebuildMode.IF_ABSENT);
    }

    @Test
    void loserOfRebuildLockWaitsOnlyWithinBudgetThenSeesReadyIndex()
            throws InterruptedException {
        when(index.state(1001L)).thenReturn(
                IndexState.MISSING, IndexState.MISSING, IndexState.READY);
        when(rebuilder.rebuild(1001L, RebuildMode.IF_ABSENT))
                .thenReturn(RebuildResult.skippedLocked());

        assertThat(coordinator.ensureAvailable(1001L))
                .isEqualTo(IndexLoadOutcome.AVAILABLE);
        verify(sleeper, times(2)).sleep(Duration.ofMillis(50));
    }

    @Test
    void exhaustedWaitReturnsUnavailableWithoutSpinningForever()
            throws InterruptedException {
        when(index.state(1001L)).thenReturn(IndexState.MISSING);
        when(rebuilder.rebuild(1001L, RebuildMode.IF_ABSENT))
                .thenReturn(RebuildResult.skippedLocked());

        assertThat(coordinator.ensureAvailable(1001L))
                .isEqualTo(IndexLoadOutcome.UNAVAILABLE);
        verify(sleeper, times(10)).sleep(Duration.ofMillis(50));
    }

    @Test
    void interruptionStopsWaitingAndRestoresTheInterruptFlag()
            throws InterruptedException {
        when(index.state(1001L)).thenReturn(IndexState.MISSING);
        when(rebuilder.rebuild(1001L, RebuildMode.IF_ABSENT))
                .thenReturn(RebuildResult.skippedLocked());
        doThrow(new InterruptedException("stop")).when(sleeper).sleep(any());

        assertThat(coordinator.ensureAvailable(1001L))
                .isEqualTo(IndexLoadOutcome.UNAVAILABLE);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void asynchronousEntryPointRequestsAForcedRebuild() {
        coordinator.requestRebuild(1001L);

        verify(rebuilder).rebuild(1001L, RebuildMode.FORCE);
    }
}
