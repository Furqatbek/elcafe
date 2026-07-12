package com.elcafe.exception;

import com.elcafe.utils.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes the standard {@link ApiResponse} error envelope from places that run OUTSIDE Spring MVC —
 * servlet filters and the security entry points — so every error body has one shape (EH-0.3).
 * Before this, TenantEnforcementFilter/SubscriptionEnforcementFilter hand-wrote ad-hoc JSON and
 * unauthenticated requests got Spring's default empty body.
 */
public final class ApiErrorWriter {

    /** Filters have no MVC message-converter chain, so serialize with a locally configured mapper. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private ApiErrorWriter() {
    }

    public static void write(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(ApiResponse.error(code, message)));
    }
}
