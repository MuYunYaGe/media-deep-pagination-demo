package com.example.mediapagination.application.port;

import java.time.Duration;
import java.util.Optional;

public interface LeaseLock {

    Optional<Lease> tryAcquire(String key, Duration ttl);

    boolean release(Lease lease);
}
