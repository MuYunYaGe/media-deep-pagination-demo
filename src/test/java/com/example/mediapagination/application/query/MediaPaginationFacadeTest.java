package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.MediaPageQuery;
import com.example.mediapagination.application.model.MediaPageResult;
import com.example.mediapagination.application.model.PageStrategy;
import com.example.mediapagination.application.model.CacheStatus;
import com.example.mediapagination.application.model.UnsupportedStrategyException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaPaginationFacadeTest {

    @Test
    void routesAQueryToItsDeclaredStrategy() {
        MediaPageHandler offset = mock(MediaPageHandler.class);
        MediaPageQuery query = new MediaPageQuery(1001L, PageStrategy.OFFSET, 1, 20);
        MediaPageResult expected = new MediaPageResult(1001L, 1, 20, 0L, 0L,
                false, PageStrategy.OFFSET, CacheStatus.NOT_USED, false, List.of());
        when(offset.strategy()).thenReturn(PageStrategy.OFFSET);
        when(offset.query(query)).thenReturn(expected);

        MediaPaginationFacade facade = new MediaPaginationFacade(List.of(offset));

        assertThat(facade.query(query)).isSameAs(expected);
        verify(offset).query(query);
    }

    @Test
    void rejectsAQueryWhenItsStrategyHasNoHandler() {
        MediaPageHandler offset = mock(MediaPageHandler.class);
        when(offset.strategy()).thenReturn(PageStrategy.OFFSET);
        MediaPaginationFacade facade = new MediaPaginationFacade(List.of(offset));

        assertThatThrownBy(() -> facade.query(
                new MediaPageQuery(1001L, PageStrategy.ZSET, 1, 20)))
                .isInstanceOf(UnsupportedStrategyException.class);
    }
}
