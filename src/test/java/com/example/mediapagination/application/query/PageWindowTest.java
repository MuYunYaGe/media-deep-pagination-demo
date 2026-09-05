package com.example.mediapagination.application.query;

import com.example.mediapagination.application.model.PageOutsideWindowException;
import com.example.mediapagination.application.model.RankRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageWindowTest {

    private final PageWindow window = new PageWindow(30_000);

    @Test
    void pageThreeThousandMapsToRanks29990Through29999() {
        assertThat(window.ranks(3_000, 10))
                .isEqualTo(new RankRange(29_990, 29_999));
    }

    @Test
    void firstPageOutsideTheConfiguredWindowIsRejected() {
        assertThatThrownBy(() -> window.ranks(3_001, 10))
                .isInstanceOf(PageOutsideWindowException.class)
                .hasMessageContaining("30000");
    }

    @Test
    void offsetUsesLongArithmeticInsteadOfOverflowingAnInt() {
        assertThat(window.offset(2_000_000_000, 100))
                .isEqualTo(199_999_999_900L);
    }

    @Test
    void pageAndSizeValidationRejectsInvalidRequests() {
        assertThatThrownBy(() -> window.offset(0, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> window.offset(1, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
