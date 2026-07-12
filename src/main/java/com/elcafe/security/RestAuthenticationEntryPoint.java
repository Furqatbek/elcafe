package com.elcafe.security;

import com.elcafe.exception.ApiErrorWriter;
import com.elcafe.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * EH-0.4: unauthenticated requests get the standard JSON envelope instead of Spring's default
 * empty-body 403 (no httpBasic/formLogin is configured, so the framework fallback was
 * {@code Http403ForbiddenEntryPoint}). Status is 401 — the correct semantics — and the code tells
 * the client what to do: {@code TOKEN_EXPIRED} → try the refresh flow; {@code UNAUTHENTICATED}
 * (no/invalid token) → go to login.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** Set by {@link JwtAuthenticationFilter} when the presented token failed because it expired. */
    public static final String TOKEN_EXPIRED_ATTR = "com.elcafe.security.tokenExpired";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        boolean expired = Boolean.TRUE.equals(request.getAttribute(TOKEN_EXPIRED_ATTR));
        ErrorCode code = expired ? ErrorCode.TOKEN_EXPIRED : ErrorCode.UNAUTHENTICATED;
        String message = expired
                ? "Your session has expired. Please sign in again."
                : "Authentication required";
        ApiErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, code, message);
    }
}
