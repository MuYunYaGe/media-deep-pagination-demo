package com.example.mediapagination.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeIncomingTraceIdAndAlwaysCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.HEADER_NAME, "trace-123");
        FilterChain chain = (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo("trace-123");

        new TraceIdFilter().doFilter(request, response, chain);

        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME))
                .isEqualTo("trace-123");
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void rejectsUnsafeIncomingValueAndReturnsGeneratedId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(TraceIdFilter.HEADER_NAME, "unsafe trace value");

        new TraceIdFilter().doFilter(request, response,
                (ignoredRequest, ignoredResponse) ->
                        assertThat(MDC.get(TraceIdFilter.MDC_KEY))
                                .matches("[0-9a-f]{32}"));

        assertThat(response.getHeader(TraceIdFilter.HEADER_NAME))
                .matches("[0-9a-f]{32}");
    }

    @Test
    void restoresPriorContextEvenWhenTheChainFails() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(TraceIdFilter.MDC_KEY, "outer");
        FilterChain failingChain = (ignoredRequest, ignoredResponse) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> new TraceIdFilter()
                .doFilter(request, response, failingChain))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isEqualTo("outer");
    }
}
