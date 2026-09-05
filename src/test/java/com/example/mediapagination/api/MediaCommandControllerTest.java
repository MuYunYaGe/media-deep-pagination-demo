package com.example.mediapagination.api;

import com.example.mediapagination.application.command.MediaCommandService;
import com.example.mediapagination.application.model.ChangeMediaStatusCommand;
import com.example.mediapagination.application.model.CreateMediaCommand;
import com.example.mediapagination.application.model.MediaNotFoundException;
import com.example.mediapagination.domain.Media;
import com.example.mediapagination.domain.MediaStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaCommandController.class)
@Import(GlobalExceptionHandler.class)
class MediaCommandControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-05T10:00:00Z");

    @Autowired private MockMvc mockMvc;
    @MockBean private MediaCommandService service;

    @Test
    void createReturnsThePersistedMediaWithCreatedStatus() throws Exception {
        when(service.create(any(CreateMediaCommand.class)))
                .thenReturn(media(101L, 1001L, MediaStatus.PUBLISHED));

        mockMvc.perform(post("/api/v1/media")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":1001,"title":"demo","publishTime":"2026-09-05T10:00:00Z","status":"PUBLISHED"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101L))
                .andExpect(jsonPath("$.categoryId").value(1001L))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void missingMediaReturnsStableNotFoundCode() throws Exception {
        when(service.changeStatus(new ChangeMediaStatusCommand(
                404L, MediaStatus.OFFLINE)))
                .thenThrow(new MediaNotFoundException(404L));

        mockMvc.perform(patch("/api/v1/media/404/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFFLINE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
    }

    @Test
    void invalidCreateBodyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/media")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":0,"title":"","publishTime":null,"status":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private static Media media(long id, long categoryId, MediaStatus status) {
        return new Media(id, categoryId, "demo", NOW, status, NOW, NOW);
    }
}
