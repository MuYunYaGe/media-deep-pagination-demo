package com.example.mediapagination.api;

import com.example.mediapagination.application.command.MediaCommandService;
import com.example.mediapagination.application.model.ChangeMediaCategoryCommand;
import com.example.mediapagination.application.model.ChangeMediaStatusCommand;
import com.example.mediapagination.application.model.ChangePublishTimeCommand;
import com.example.mediapagination.domain.Media;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/media")
public class MediaCommandController {

    private final MediaCommandService service;

    public MediaCommandController(MediaCommandService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Media create(@Valid @RequestBody CreateMediaRequest request) {
        return service.create(request.toCommand());
    }

    @PatchMapping("/{id}/status")
    public Media changeStatus(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(new ChangeMediaStatusCommand(id, request.status()));
    }

    @PatchMapping("/{id}/category")
    public Media changeCategory(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ChangeCategoryRequest request) {
        return service.changeCategory(
                new ChangeMediaCategoryCommand(id, request.categoryId()));
    }

    @PatchMapping("/{id}/publish-time")
    public Media changePublishTime(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody ChangePublishTimeRequest request) {
        return service.changePublishTime(
                new ChangePublishTimeCommand(id, request.publishTime()));
    }
}
