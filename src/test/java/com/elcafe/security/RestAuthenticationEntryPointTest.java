package com.elcafe.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins EH-0.4: the security boundary returns the standard envelope — a 401 with a code the client
 * can branch on (refresh vs login) — instead of the framework default empty-body 403.
 */
class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("no/invalid token → 401 UNAUTHENTICATED envelope")
    void unauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("nope"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("success").asBoolean()).isFalse();
        assertThat(body.get("error").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.get("message").asText()).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("expired token (filter-marked) → 401 TOKEN_EXPIRED so the client runs the refresh flow")
    void expiredToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RestAuthenticationEntryPoint.TOKEN_EXPIRED_ATTR, Boolean.TRUE);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new InsufficientAuthenticationException("expired"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode body = mapper.readTree(response.getContentAsString());
        assertThat(body.get("error").asText()).isEqualTo("TOKEN_EXPIRED");
    }
}
