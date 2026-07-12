package com.elcafe.exception;

import com.elcafe.utils.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the EH-0 error contract (docs/ERROR_HANDLING_PLAN.md): every handler emits the standard
 * envelope with a stable machine code and the right status, and server faults never leak
 * exception internals to the client — the UI localizes from {@code error}, so these literals are
 * load-bearing for the frontend dictionary.
 */
class GlobalExceptionHandlerContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("typed business exceptions → their status + code + human message")
    void typedExceptionsCarryCodes() {
        assertEnvelope(handler.handleResourceNotFoundException(
                        new ResourceNotFoundException("Order", "id", 9L), null),
                HttpStatus.NOT_FOUND, "NOT_FOUND");
        assertEnvelope(handler.handleBadRequestException(new BadRequestException("Bad input"), null),
                HttpStatus.BAD_REQUEST, "BAD_REQUEST");
        assertEnvelope(handler.handleConflictException(new ConflictException("Already paid"), null),
                HttpStatus.CONFLICT, "CONFLICT");
        assertEnvelope(handler.handleForbiddenException(new ForbiddenException("Nope"), null),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertEnvelope(handler.handleUnauthorizedException(new UnauthorizedException("Who?"), null),
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    @DisplayName("EH-1.2 bridge: IllegalArgumentException is a 400 with its human message, not a 500")
    void illegalArgumentIsBadRequest() {
        ResponseEntity<ApiResponse<Void>> r =
                handler.handleIllegalArgument(new IllegalArgumentException("Email already in use"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().getError()).isEqualTo("BAD_REQUEST");
        assertThat(r.getBody().getMessage()).isEqualTo("Email already in use");
    }

    @Test
    @DisplayName("EH-1.2 bridge: IllegalStateException stays generic — internals never reach the client")
    void illegalStateIsGeneric500() {
        ResponseEntity<ApiResponse<Void>> r = handler.handleIllegalState(
                new IllegalStateException("HikariPool-1 - connection leak on OrderRepositoryImpl"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().getError()).isEqualTo("INTERNAL");
        assertThat(r.getBody().getMessage())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("Hikari");
    }

    @Test
    @DisplayName("catch-all 500 never echoes the exception message")
    void catchAllNeverLeaks() {
        ResponseEntity<ApiResponse<Void>> r = handler.handleGlobalException(
                new RuntimeException("SELECT * FROM users WHERE email='x' failed: duplicate key"), null);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r.getBody().getError()).isEqualTo("INTERNAL");
        assertThat(r.getBody().getMessage()).doesNotContain("SELECT", "duplicate key");
    }

    @Test
    @DisplayName("DB constraint violations are a sanitized 409, not a 500 with SQL in it")
    void dataIntegrityIsSanitizedConflict() {
        ResponseEntity<ApiResponse<Void>> r = handler.handleDataIntegrity(
                new DataIntegrityViolationException(
                        "could not execute statement [duplicate key value violates unique constraint \"uk_users_email\"]"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().getError()).isEqualTo("CONFLICT");
        assertThat(r.getBody().getMessage()).doesNotContain("constraint", "uk_users_email");
    }

    @Test
    @DisplayName("auth failures: fixed messages + distinct codes for the client to branch on")
    void authFailures() {
        assertEnvelope(handler.handleBadCredentialsException(new BadCredentialsException("boom"), null),
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertEnvelope(handler.handleAccessDeniedException(new AccessDeniedException("secret path"), null),
                HttpStatus.FORBIDDEN, "FORBIDDEN");
        // the raw exception text must not pass through for these two
        assertThat(handler.handleBadCredentialsException(new BadCredentialsException("boom"), null)
                .getBody().getMessage()).isEqualTo("Invalid credentials");
    }

    private void assertEnvelope(ResponseEntity<ApiResponse<Void>> r, HttpStatus status, String code) {
        assertThat(r.getStatusCode()).isEqualTo(status);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().isSuccess()).isFalse();
        assertThat(r.getBody().getError()).isEqualTo(code);
        assertThat(r.getBody().getMessage()).isNotBlank();
    }
}
