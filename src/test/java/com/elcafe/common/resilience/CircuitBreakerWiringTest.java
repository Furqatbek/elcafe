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
}
