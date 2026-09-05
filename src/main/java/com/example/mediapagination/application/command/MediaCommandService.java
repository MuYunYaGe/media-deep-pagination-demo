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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

@Service
public class MediaCommandService {

    private final MediaQueryStore queryStore;
    private final MediaCommandStore commandStore;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MediaCommandService(
            MediaQueryStore queryStore,
            MediaCommandStore commandStore,
            ApplicationEventPublisher events,
            Clock clock) {
        this.queryStore = Objects.requireNonNull(queryStore, "queryStore");
        this.commandStore = Objects.requireNonNull(commandStore, "commandStore");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Media create(CreateMediaCommand command) {
        Media created = commandStore.insert(Objects.requireNonNull(command, "command"));
        events.publishEvent(new MediaChangedEvent(null, created));
        return created;
    }

    @Transactional
    public Media changeStatus(ChangeMediaStatusCommand command) {
        Objects.requireNonNull(command, "command");
        Media before = requireMedia(command.id());
        Media after = commandStore.updateStatus(
                command.id(), command.status(), clock.instant());
        events.publishEvent(new MediaChangedEvent(before, after));
        return after;
    }

    @Transactional
    public Media changeCategory(ChangeMediaCategoryCommand command) {
        Objects.requireNonNull(command, "command");
        Media before = requireMedia(command.id());
        Media after = commandStore.updateCategory(
                command.id(), command.categoryId(), clock.instant());
        events.publishEvent(new MediaChangedEvent(before, after));
        return after;
    }

    @Transactional
    public Media changePublishTime(ChangePublishTimeCommand command) {
        Objects.requireNonNull(command, "command");
        Media before = requireMedia(command.id());
        Media after = commandStore.updatePublishTime(
                command.id(), command.publishTime(), clock.instant());
        events.publishEvent(new MediaChangedEvent(before, after));
        return after;
    }

    private Media requireMedia(long id) {
        return queryStore.findById(id)
                .orElseThrow(() -> new MediaNotFoundException(id));
    }
}
