package com.example.mediapagination.api;

import com.example.mediapagination.application.cache.MediaIndexRebuilder;
import com.example.mediapagination.application.command.DemoDataGenerator;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Profile("local")
@RestController
@RequestMapping("/api/v1/admin/data")
public class DemoDataController {

    private final DemoDataGenerator generator;
    private final MediaIndexRebuilder rebuilder;

    public DemoDataController(DemoDataGenerator generator,
                              MediaIndexRebuilder rebuilder) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.rebuilder = Objects.requireNonNull(rebuilder, "rebuilder");
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate deterministic local demo data",
            description = "Local profile only. Inserts bounded batches and synchronously rebuilds the category index.")
    public GenerateMediaResponse generate(
            @Valid @RequestBody GenerateMediaRequest request) {
        long inserted = generator.generate(request.toCommand());
        RebuildResult rebuild = rebuilder.rebuild(
                request.categoryId(), RebuildMode.FORCE);
        return new GenerateMediaResponse(
                inserted,
                rebuild.status().name(),
                rebuild.entryCount(),
                rebuild.duration().toMillis());
    }

    public record GenerateMediaResponse(
            long inserted,
            String rebuildStatus,
            long indexedEntries,
            long rebuildDurationMs
    ) {
    }
}
