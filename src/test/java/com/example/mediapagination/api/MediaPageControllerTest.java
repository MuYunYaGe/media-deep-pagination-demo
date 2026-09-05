package com.example.mediapagination.api;

import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageOutsideWindowException;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.query.MediaPaginationFacade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaPageController.class)
@Import({GlobalExceptionHandler.class, PageStrategyConverter.class})
class MediaPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaPaginationFacade facade;

    @Test
    void exposesLowercaseOffsetStrategyAndPaginationMetadata() throws Exception {
        when(facade.query(new MediaPageQuery(1001L, PageStrategy.OFFSET, 2, 10)))
                .thenReturn(new MediaPageResult(1001L, 2, 10, 21L, 3L,
                        false, PageStrategy.OFFSET, CacheStatus.NOT_USED,
                        false, List.of()));

        mockMvc.perform(get("/api/v1/categories/1001/media")
                        .param("strategy", "offset")
                        .param("page", "2")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value(1001))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.strategy").value("offset"))
                .andExpect(jsonPath("$.cacheStatus").value("NOT_USED"));
    }

    @Test
    void validationFailureUsesStableErrorCode() throws Exception {
        mockMvc.perform(get("/api/v1/categories/1001/media")
                        .param("strategy", "offset")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }

    @Test
    void cacheWindowViolationUsesStableErrorCode() throws Exception {
        when(facade.query(any())).thenThrow(
                new PageOutsideWindowException(30_000L, 30_009L, 30_000));

        mockMvc.perform(get("/api/v1/categories/1001/media")
                        .param("strategy", "zset")
                        .param("page", "3001")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_OUTSIDE_CACHE_WINDOW"));
    }

    @Test
    void unknownStrategyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/categories/1001/media")
                        .param("strategy", "cursor"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PAGE_REQUEST"));
    }
}
