package com.elcafe.common.resilience;

import com.elcafe.modules.customer.service.GeocodingService;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.service.InstagramApiClient;
import com.elcafe.modules.sms.dto.SendSmsRequest;
import com.elcafe.modules.sms.service.SmsService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the OPS-4 circuit-breaker wiring end-to-end through the Spring AOP proxy: with a breaker
 * forced OPEN, each outbound client fails fast through its typed fallback — no network attempt, no
 * timeout wait — and each honours its original failure contract (geocoding/SMS: the RuntimeException
 * callers already handle; Instagram: boolean false). If an annotation, fallback signature, or yaml
 * instance name drifts, these tests break instead of the wiring silently degrading to no-op.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cbwiring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Unroutable: the breaker test needs calls that genuinely fail at the transport layer.
        "instagram.graph.base-url=http://127.0.0.1:1",
})
class CircuitBreakerWiringTest {

    @Autowired private CircuitBreakerRegistry registry;
    @Autowired private GeocodingService geocodingService;
    @Autowired private SmsService smsService;
    @Autowired private InstagramApiClient instagramApiClient;

    @AfterEach
    void closeBreakers() {
        registry.getAllCircuitBreakers().forEach(io.github.resilience4j.circuitbreaker.CircuitBreaker::reset);
    }

    private Duration timed(Runnable call) {
        long start = System.nanoTime();
        try {
            call.run();
        } catch (RuntimeException ignored) {
            // the assertion of interest is made by the caller
        }
        return Duration.ofNanos(System.nanoTime() - start);
    }

    @Test
    @DisplayName("open geocoding breaker → immediate RuntimeException, no provider call")
    void geocodingFailsFastWhenOpen() {
        registry.circuitBreaker("geocoding").transitionToOpenState();

        assertThatThrownBy(() -> geocodingService.reverseGeocode(41.31, 69.24))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("circuit open");

        // Fail-fast: far below the client's 10s read timeout (no network attempt happens at all).
        assertThat(timed(() -> geocodingService.reverseGeocode(41.31, 69.24)))
                .isLessThan(Duration.ofSeconds(2));
        assertThat(registry.circuitBreaker("geocoding").getMetrics().getNumberOfNotPermittedCalls())
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("open sms breaker → immediate RuntimeException with the contract callers handle")
    void smsFailsFastWhenOpen() {
        registry.circuitBreaker("sms").transitionToOpenState();

        assertThatThrownBy(() -> smsService.sendSms(new SendSmsRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("circuit open");
    }

    @Test
    @DisplayName("open instagram breaker → false (the boolean not-delivered contract), no API call")
    void instagramReturnsFalseWhenOpen() {
        registry.circuitBreaker("instagram").transitionToOpenState();

        boolean delivered = instagramApiClient.sendMessage(new InstagramBotConfig(), "igsid-1", "hi");

        assertThat(delivered).isFalse();
    }

    @Test
    @DisplayName("real Instagram failures actually TRIP the breaker — not just a forced-open state")
    void instagramBreakerOpensOnRealFailures() {
        io.github.resilience4j.circuitbreaker.CircuitBreaker breaker = registry.circuitBreaker("instagram");
        breaker.reset();
        assertThat(breaker.getState())
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.CLOSED);

        // Every other test in this class forces transitionToOpenState(), which proves the fallback
        // signature and nothing else. This one drives genuine failures instead, because the bug it
        // guards was invisible to a forced-open test: the client used to catch RestClientException
        // INSIDE the @CircuitBreaker method, so resilience4j recorded a 100% success rate no matter
        // how broken Meta was, and the breaker could never open on its own.
        InstagramBotConfig config = InstagramBotConfig.builder()
                .instagramAccountId("17841400000000000")   // well-formed, so the call is attempted
                .accessToken("invalid-token")
                .build();

        // The configured host is unroutable in tests, so each call fails at the transport layer.
        for (int i = 0; i < 10; i++) {
            assertThat(instagramApiClient.sendMessage(config, "igsid-1", "hi")).isFalse();
        }

        assertThat(breaker.getMetrics().getNumberOfFailedCalls())
                .as("failures must be RECORDED by the breaker, not swallowed inside the guarded call")
                .isGreaterThan(0);
        assertThat(breaker.getState())
                .as("10 consecutive failures past a 5-call minimum and 50%% threshold must open it")
                .isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN);
    }
}
