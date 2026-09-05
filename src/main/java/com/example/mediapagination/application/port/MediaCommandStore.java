package com.example.mediapagination.application.port;

import com.example.mediapagination.application.model.CreateMediaCommand;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;

import java.time.Instant;

public interface MediaCommandStore {

    Media insert(CreateMediaCommand command);

    Media updateStatus(long id, MediaStatus status, Instant updatedAt);

    Media updateCategory(long id, long categoryId, Instant updatedAt);

    Media updatePublishTime(long id, Instant publishTime, Instant updatedAt);
}
