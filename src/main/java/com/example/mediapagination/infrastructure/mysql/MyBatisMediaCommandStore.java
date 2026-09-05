package com.example.mediapagination.infrastructure.mysql;

import com.example.mediapagination.application.model.CreateMediaCommand;
import com.example.mediapagination.application.model.MediaNotFoundException;
import com.example.mediapagination.application.port.MediaCommandStore;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Repository
public class MyBatisMediaCommandStore implements MediaCommandStore {

    private final MediaCommandMapper commands;
    private final MediaQueryMapper queries;
    private final Clock clock;

    public MyBatisMediaCommandStore(
            MediaCommandMapper commands, MediaQueryMapper queries, Clock clock) {
        this.commands = Objects.requireNonNull(commands, "commands");
        this.queries = Objects.requireNonNull(queries, "queries");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Media insert(CreateMediaCommand command) {
        Objects.requireNonNull(command, "command");
        Instant now = clock.instant();
        MediaRow row = new MediaRow();
        row.setCategoryId(command.categoryId());
        row.setTitle(command.title());
        row.setPublishTime(command.publishTime());
        row.setStatus(command.status());
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        if (commands.insert(row) != 1 || row.getId() <= 0) {
            throw new IllegalStateException("media insert did not return one generated ID");
        }
        return row.toDomain();
    }

    @Override
    public Media updateStatus(long id, MediaStatus status, Instant updatedAt) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        requireUpdated(id, commands.updateStatus(id, status, updatedAt));
        return load(id);
    }

    @Override
    public Media updateCategory(long id, long categoryId, Instant updatedAt) {
        Objects.requireNonNull(updatedAt, "updatedAt");
        requireUpdated(id, commands.updateCategory(id, categoryId, updatedAt));
        return load(id);
    }

    @Override
    public Media updatePublishTime(long id, Instant publishTime, Instant updatedAt) {
        Objects.requireNonNull(publishTime, "publishTime");
        Objects.requireNonNull(updatedAt, "updatedAt");
        requireUpdated(id, commands.updatePublishTime(id, publishTime, updatedAt));
        return load(id);
    }

    private void requireUpdated(long id, int affectedRows) {
        if (affectedRows == 0) {
            throw new MediaNotFoundException(id);
        }
        if (affectedRows != 1) {
            throw new IllegalStateException("media update affected " + affectedRows + " rows");
        }
    }

    private Media load(long id) {
        return queries.findById(id)
                .map(MediaRow::toDomain)
                .orElseThrow(() -> new MediaNotFoundException(id));
    }
}
