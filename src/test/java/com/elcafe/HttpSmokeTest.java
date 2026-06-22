package com.elcafe;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live-server HTTP smoke test: boots the app on a real embedded Tomcat (random port) and drives it with
 * real HTTP requests through {@link TestRestTemplate}.
 *
 * <p>Where {@link ApplicationContextSmokeTest} proves the context <em>wires</em>, this proves the
 * <em>request path works over the wire</em>: the servlet container serves, MVC + actuator route, and —
 * most importantly — the Spring Security filter chain actually executes on a request, letting a
 * permit-all endpoint through while rejecting unauthenticated and malformed-token access. None of the
 * MockMvc / standalone tests exercise the embedded server or the full production filter chain end to end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Same H2 'public' schema pre-creation as ApplicationContextSmokeTest (see note there): the
        // full-context datasource runs with DATABASE_TO_UPPER=FALSE, so default_schema=public must exist.
        "spring.datasource.url=jdbc:h2:mem:httpsmoketestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        // Redis isn't running under test, so its health contributor would force /actuator/health to 503.
        // Disable just that indicator; the point here is that the endpoint is served, not Redis liveness.
        "management.health.redis.enabled=false",
        // The malformed-token test deliberately makes the JWT filter log a parse failure (its real
        // fail-closed behaviour). Mute just that logger so a passing test doesn't dump a stack trace.
        "logging.level.com.elcafe.security.JwtAuthenticationFilter=OFF",
})
class HttpSmokeTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void healthEndpointIsServedAndUp() {
        // Permit-all endpoint: proves Tomcat is listening, MVC + actuator are routed, and security lets
        // a public path through the chain.
        ResponseEntity<String> res = rest.getForEntity("/actuator/health", String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void protectedEndpointWithoutAuthIsRejected() {
        // anyRequest().authenticated(): the security chain must block this before it ever reaches a
        // controller. 401 or 403 both mean "rejected by security" (depends on the entry point).
        ResponseEntity<String> res = rest.getForEntity("/api/v1/orders", String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void malformedBearerTokenFailsClosedNotWithServerError() {
        // The JWT filter must process a garbage Authorization header and fail closed (stay
        // unauthenticated → 401/403), never let the exception surface as a 500.
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("this.is.not.a.jwt");
        ResponseEntity<String> res = rest.exchange(
                "/api/v1/orders", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(res.getStatusCode()).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }
}
