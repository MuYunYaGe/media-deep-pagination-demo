package com.example.mediapagination.application.command;

import com.example.mediapagination.application.model.ChangeMediaCategoryCommand;
import com.example.mediapagination.application.model.ChangeMediaStatusCommand;
import com.example.mediapagination.application.model.ChangePublishTimeCommand;
import com.example.mediapagination.application.model.CreateMediaCommand;
import com.example.mediapagination.application.model.MediaChangedEvent;
import com.example.mediapagination.application.model.MediaNotFoundException;
import com.example.mediapagination.application.port.MediaCommandStore;
import com.example.mediapagination.application.port.MediaQueryStore;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");

    @Mock private MediaQueryStore queryStore;
    @Mock private MediaCommandStore commandStore;
    @Mock private ApplicationEventPublisher events;

    private MediaCommandService service;

    @BeforeEach
    void setUp() {
        service = new MediaCommandService(queryStore, commandStore, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void statusChangePublishesOneBeforeAndAfterEvent() {
        Media before = media(10L, 1001L, MediaStatus.PUBLISHED, NOW);
        Media after = media(10L, 1001L, MediaStatus.OFFLINE, NOW);
        when(queryStore.findById(10L)).thenReturn(Optional.of(before));
        when(commandStore.updateStatus(10L, MediaStatus.OFFLINE, NOW))
                .thenReturn(after);

        assertThat(service.changeStatus(
                new ChangeMediaStatusCommand(10L, MediaStatus.OFFLINE)))
                .isEqualTo(after);
        verify(events).publishEvent(new MediaChangedEvent(before, after));
    }

    @Test
    void createPublishesAnEventWithNoBeforeSnapshot() {
        CreateMediaCommand command = new CreateMediaCommand(
                1001L, "new media", NOW, MediaStatus.PUBLISHED);
        Media created = media(11L, 1001L, MediaStatus.PUBLISHED, NOW);
        when(commandStore.insert(command)).thenReturn(created);

        assertThat(service.create(command)).isEqualTo(created);
        verify(events).publishEvent(new MediaChangedEvent(null, created));
    }

    @Test
    void missingMediaStopsBeforeTheUpdate() {
        when(queryStore.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeCategory(
                new ChangeMediaCategoryCommand(404L, 1002L)))
                .isInstanceOf(MediaNotFoundException.class);
        verify(commandStore, never()).updateCategory(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishTimeChangeUsesTheApplicationClockForUpdatedAt() {
        Instant changedTime = NOW.plusSeconds(60);
        Media before = media(10L, 1001L, MediaStatus.PUBLISHED, NOW);
        Media after = media(10L, 1001L, MediaStatus.PUBLISHED, changedTime);
        when(queryStore.findById(10L)).thenReturn(Optional.of(before));
        when(commandStore.updatePublishTime(10L, changedTime, NOW)).thenReturn(after);

        assertThat(service.changePublishTime(
                new ChangePublishTimeCommand(10L, changedTime))).isEqualTo(after);
        verify(commandStore).updatePublishTime(10L, changedTime, NOW);
    }

    private static Media media(long id, long categoryId, MediaStatus status,
                               Instant publishTime) {
        return new Media(id, categoryId, "media-" + id, publishTime,
                status, NOW, NOW);
    }
}
