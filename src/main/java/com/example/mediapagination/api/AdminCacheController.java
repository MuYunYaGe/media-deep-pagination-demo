package com.example.mediapagination.api;

import com.example.mediapagination.application.cache.MediaIndexRebuilder;
import com.example.mediapagination.application.model.RebuildMode;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCacheController {

    private final MediaIndexRebuilder rebuilder;

    public AdminCacheController(MediaIndexRebuilder rebuilder) {
        this.rebuilder = rebuilder;
    }

    @Operation(
            summary = "Force rebuild one category index",
            description = "Local demonstration endpoint; production deployments must add authorization.")
    @PostMapping("/{categoryId}/cache/rebuild")
    public RebuildResponse rebuild(@PathVariable @Min(1) long categoryId) {
        return RebuildResponse.from(
                rebuilder.rebuild(categoryId, RebuildMode.FORCE));
    }
}
