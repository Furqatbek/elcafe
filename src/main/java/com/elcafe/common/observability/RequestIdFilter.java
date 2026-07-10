package com.elcafe.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every request with a correlation id so a single request is traceable across all of its log
 * lines (audit OPS-3 — the app had no request ids, making "trace this one failing request" impossible).
 *
 * <p>Honors an inbound {@code X-Request-Id} (so an upstream proxy / caller's id propagates), otherwise
 * generates one; puts it in the SLF4J {@link MDC} as {@code requestId} (rendered by the log pattern) and
 * echoes it on the response header. Runs first ({@code HIGHEST_PRECEDENCE}) so even the security filter
 * chain's log lines carry it, and always clears the MDC so the id can't leak onto a pooled thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank() || requestId.length() > 64) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
