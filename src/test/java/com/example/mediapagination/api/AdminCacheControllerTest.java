package com.example.mediapagination.api;

import com.example.mediapagination.application.cache.MediaIndexRebuilder;
import com.example.mediapagination.application.model.RebuildMode;
import com.example.mediapagination.application.model.RebuildResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminCacheController.class)
@Import(GlobalExceptionHandler.class)
class AdminCacheControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private MediaIndexRebuilder rebuilder;

    @Test
    void manualRebuildReturnsInspectableResult() throws Exception {
        when(rebuilder.rebuild(1001L, RebuildMode.FORCE))
                .thenReturn(RebuildResult.rebuilt(42L, Duration.ofMillis(17)));

        mockMvc.perform(post("/api/v1/admin/categories/1001/cache/rebuild"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REBUILT"))
                .andExpect(jsonPath("$.entryCount").value(42))
                .andExpect(jsonPath("$.durationMs").value(17));
    }
}
