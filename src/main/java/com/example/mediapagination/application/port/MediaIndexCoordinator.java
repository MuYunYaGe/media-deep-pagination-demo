package com.example.mediapagination.application.port;

import com.example.mediapagination.application.model.IndexLoadOutcome;

public interface MediaIndexCoordinator {

    IndexLoadOutcome ensureAvailable(long categoryId);

    void requestRebuild(long categoryId);
}
