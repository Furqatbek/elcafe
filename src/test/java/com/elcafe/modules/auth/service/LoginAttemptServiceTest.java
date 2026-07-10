package com.elcafe.modules.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
        ReflectionTestUtils.setField(service, "maxAttempts", 3);
        ReflectionTestUtils.setField(service, "lockMinutes", 15L);
        ReflectionTestUtils.setField(service, "attemptWindowMinutes", 15L);
    }

    @Test @DisplayName("not locked before the threshold")
    void belowThreshold_notLocked() {
        service.recordFailure("a@t.co");
        service.recordFailure("a@t.co");
        assertThat(service.isLocked("a@t.co")).isFalse();
    }

    @Test @DisplayName("locks at the threshold and reports unlock time")
    void atThreshold_locked() {
        service.recordFailure("a@t.co");
        service.recordFailure("a@t.co");
        service.recordFailure("a@t.co");
        assertThat(service.isLocked("a@t.co")).isTrue();
        assertThat(service.secondsUntilUnlock("a@t.co")).isGreaterThan(0);
    }

    @Test @DisplayName("success clears the counter")
    void success_clears() {
        service.recordFailure("a@t.co");
        service.recordFailure("a@t.co");
        service.recordSuccess("a@t.co");
        service.recordFailure("a@t.co"); // fresh window, only 1 failure
        assertThat(service.isLocked("a@t.co")).isFalse();
    }

    @Test @DisplayName("lockout is per-account and case/whitespace-insensitive on the email key")
    void perAccount_normalized() {
        service.recordFailure(" A@T.co ");
        service.recordFailure("a@t.co");
        service.recordFailure("A@T.CO");
        assertThat(service.isLocked("a@t.co")).isTrue();   // same account
        assertThat(service.isLocked("other@t.co")).isFalse(); // different account unaffected
    }

    @Test @DisplayName("unknown account is never locked")
    void unknown_notLocked() {
        assertThat(service.isLocked("nobody@t.co")).isFalse();
        assertThat(service.secondsUntilUnlock("nobody@t.co")).isZero();
    }
}
