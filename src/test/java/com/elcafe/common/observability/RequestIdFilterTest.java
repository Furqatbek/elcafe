package com.elcafe.common.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("generates a request id, exposes it on the MDC + response header, and clears the MDC after")
    void generatesAndClears() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seenInMdc = new String[1];
        FilterChain chain = mock(FilterChain.class);
        doAnswer(i -> { seenInMdc[0] = MDC.get(RequestIdFilter.MDC_KEY); return null; })
                .when(chain).doFilter(req, res);

        filter.doFilter(req, res, chain);

        assertThat(seenInMdc[0]).isNotBlank();                                  // available during the request
        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo(seenInMdc[0]);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();                  // cleared afterwards (no thread leak)
    }

    @Test
    @DisplayName("honors an inbound X-Request-Id so a caller's correlation id propagates")
    void honorsInboundId() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(RequestIdFilter.HEADER, "upstream-123");
        MockHttpServletResponse res = new MockHttpServletResponse();
        String[] seen = new String[1];
        FilterChain chain = mock(FilterChain.class);
        doAnswer(i -> { seen[0] = MDC.get(RequestIdFilter.MDC_KEY); return null; })
                .when(chain).doFilter(req, res);

        filter.doFilter(req, res, chain);

        assertThat(seen[0]).isEqualTo("upstream-123");
        assertThat(res.getHeader(RequestIdFilter.HEADER)).isEqualTo("upstream-123");
    }
}
