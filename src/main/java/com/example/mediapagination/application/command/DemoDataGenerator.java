package com.example.mediapagination.application.command;

import com.example.mediapagination.application.model.GenerateMediaCommand;
import com.example.mediapagination.application.model.SeedMedia;
import com.example.mediapagination.application.port.MediaCommandStore;
import com.example.mediapagination.domain.MediaStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class DemoDataGenerator {

    private static final Instant BASE_EPOCH = Instant.parse("2020-01-01T00:00:00Z");
    private static final long SEED_RANGE_SECONDS = 10_000_000L;

    private final MediaCommandStore store;

    public DemoDataGenerator(MediaCommandStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public long generate(GenerateMediaCommand command) {
        Objects.requireNonNull(command, "command");
        Instant base = BASE_EPOCH.plusSeconds(
                Math.floorMod(command.seed(), SEED_RANGE_SECONDS));
        long inserted = 0L;
        for (int first = 0; first < command.count(); first += command.batchSize()) {
            int end = Math.min(command.count(), first + command.batchSize());
            List<SeedMedia> batch = new ArrayList<>(end - first);
            for (int ordinal = first; ordinal < end; ordinal++) {
                Instant publishTime = base.plusMillis(ordinal / 3L);
                batch.add(new SeedMedia(
                        command.categoryId(),
                        String.format(Locale.ROOT, "Demo media %06d", ordinal),
                        publishTime,
                        MediaStatus.PUBLISHED,
                        publishTime,
                        publishTime));
            }
            store.insertSeedBatch(List.copyOf(batch));
            inserted += batch.size();
        }
        return inserted;
    }
}
