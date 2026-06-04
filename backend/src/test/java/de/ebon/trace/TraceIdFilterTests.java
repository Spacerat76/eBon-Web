package de.ebon.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceIdFilterTests {

    // Verifies caller-provided trace IDs flow into MDC, response headers, and are cleaned up afterward.
    @Test
    void usesProvidedTraceIdAndCleansUpMdcAfterRequest() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Trace-Id", "trace-123");

        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(MDC.get("traceId")).isEqualTo("trace-123");
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute("traceId")).isEqualTo("trace-123");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("trace-123");
        assertThat(MDC.get("traceId")).isNull();
        verify(chain).doFilter(any(), any());
    }

    // Verifies requests without a trace header still receive a generated trace ID for log/error correlation.
    @Test
    void generatesTraceIdWhenHeaderIsMissing() throws ServletException, IOException {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ArgumentCaptor<jakarta.servlet.ServletRequest> requestCaptor = ArgumentCaptor.forClass(jakarta.servlet.ServletRequest.class);
        ArgumentCaptor<jakarta.servlet.ServletResponse> responseCaptor = ArgumentCaptor.forClass(jakarta.servlet.ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(requestCaptor.capture(), responseCaptor.capture());
        String traceId = (String) request.getAttribute("traceId");
        assertThat(traceId).isNotBlank();
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        assertThat(MDC.get("traceId")).isNull();
    }
}
