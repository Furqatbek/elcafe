package com.elcafe.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EH-1.6: the envelope contract through the real MVC exception plumbing (standalone MockMvc, no
 * Spring context). Every failure class renders {@code success:false} + a stable {@code error} code
 * + a message — and 5xx bodies never contain exception internals.
 */
class ErrorEnvelopeContractTest {

    @RestController
    static class BoomController {
        @GetMapping("/boom/not-found")
        String notFound() { throw new ResourceNotFoundException("Order", "id", 7L); }

        @GetMapping("/boom/conflict")
        String conflict() { throw new ConflictException("Email already in use"); }

        @GetMapping("/boom/illegal-argument")
        String illegalArgument() { throw new IllegalArgumentException("Page index must not be less than zero"); }

        @GetMapping("/boom/internal")
        String internal() { throw new IllegalStateException("HikariPool leak at OrderRepositoryImpl.java:42"); }

        record Body(@jakarta.validation.constraints.NotBlank String name) {}

        @PostMapping("/boom/validate")
        String validate(@jakarta.validation.Valid @RequestBody Body body) { return "ok"; }
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BoomController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("typed not-found → 404 envelope with code + message")
    void notFound() throws Exception {
        mvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found with id : '7'"));
    }

    @Test
    @DisplayName("conflict → 409 CONFLICT envelope")
    void conflict() throws Exception {
        mvc.perform(get("/boom/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    @DisplayName("IllegalArgumentException bridge → 400 with its human message, not a 500")
    void illegalArgumentBridge() throws Exception {
        mvc.perform(get("/boom/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Page index must not be less than zero"));
    }

    @Test
    @DisplayName("server fault → 500 generic; internals never reach the body")
    void internalNeverLeaks() throws Exception {
        mvc.perform(get("/boom/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.message", not(containsString("Hikari"))));
    }

    @Test
    @DisplayName("bean validation → 400 VALIDATION_ERROR with the field map")
    void validation() throws Exception {
        mvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    @DisplayName("malformed JSON body → 400 with a fixed message (parser detail stays in logs)")
    void malformedJson() throws Exception {
        mvc.perform(post("/boom/validate").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"));
    }
}
