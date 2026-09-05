package com.example.mediapagination.api;

import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.query.MediaPaginationFacade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/categories")
public class MediaPageController {

    private final MediaPaginationFacade facade;

    public MediaPageController(MediaPaginationFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/{categoryId}/media")
    public MediaPageResponse page(
            @PathVariable @Min(1) long categoryId,
            @RequestParam(defaultValue = "zset") PageStrategy strategy,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return MediaPageResponse.from(facade.query(
                new MediaPageQuery(categoryId, strategy, page, size)));
    }
}
