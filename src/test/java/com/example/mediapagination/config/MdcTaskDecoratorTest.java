package com.example.mediapagination.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void copiesThenCleansTheSubmittingContext() {
        MDC.put("traceId", "parent-1");
        Runnable decorated = decorator.decorate(() ->
                assertThat(MDC.get("traceId")).isEqualTo("parent-1"));
        MDC.clear();

        decorated.run();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void restoresContextAlreadyOwnedByTheWorkerThread() {
        MDC.put("traceId", "parent-1");
        Runnable decorated = decorator.decorate(() ->
                assertThat(MDC.get("traceId")).isEqualTo("parent-1"));
        MDC.put("traceId", "worker-previous");

        decorated.run();

        assertThat(MDC.get("traceId")).isEqualTo("worker-previous");
    }
}
