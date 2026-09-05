package com.example.mediapagination.application.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DirtyCategoryRegistryTest {

    @Test
    void capacityIsBoundedAndDuplicateMarksDoNotConsumeSpace() {
        DirtyCategoryRegistry registry = new DirtyCategoryRegistry(2);

        assertThat(registry.mark(1001L)).isTrue();
        assertThat(registry.mark(1001L)).isTrue();
        assertThat(registry.mark(1002L)).isTrue();
        assertThat(registry.mark(1003L)).isFalse();
        assertThat(registry.size()).isEqualTo(2);
    }

    @Test
    void drainRemovesAtMostTheRequestedCount() {
        DirtyCategoryRegistry registry = new DirtyCategoryRegistry(10);
        registry.mark(1001L);
        registry.mark(1002L);
        registry.mark(1003L);

        assertThat(registry.drain(2)).hasSize(2);
        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.drain(10)).hasSize(1);
        assertThat(registry.size()).isZero();
    }
}
