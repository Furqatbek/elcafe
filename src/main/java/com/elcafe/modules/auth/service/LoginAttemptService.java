package com.elcafe.modules.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-account brute-force lockout for staff email login. Complements the per-IP rate limit
 * ({@code @RateLimited(AUTH)}): the rate limit stops one IP hammering the login endpoint; this stops a
 * distributed attack (many IPs) grinding one account's password.
 *
 * <p>State is in-memory and ephemeral by design — a lockout is transient and does not need to survive a
 * restart (which an attacker cannot force). Keyed by normalized email. A background sweep evicts stale
 * entries so the map stays bounded.
 */
@Slf4j
@Service
public class LoginAttemptService {

    @Value("${app.security.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.login.lock-minutes:15}")
    private long lockMinutes;

    /** Failures older than this (with no lock in effect) are forgotten, so the counter is a rolling window. */
    @Value("${app.security.login.attempt-window-minutes:15}")
    private long attemptWindowMinutes;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        int count;
        Instant firstFailure;
        Instant lockedUntil;
    }

    private static String key(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    /** True if the account is currently locked out. */
    public boolean isLocked(String email) {
        Attempt a = attempts.get(key(email));
        return a != null && a.lockedUntil != null && a.lockedUntil.isAfter(Instant.now());
    }

    /** Seconds until the lockout on this account lifts (0 if not locked). */
    public long secondsUntilUnlock(String email) {
        Attempt a = attempts.get(key(email));
        if (a == null || a.lockedUntil == null) {
            return 0;
        }
        long secs = Duration.between(Instant.now(), a.lockedUntil).getSeconds();
        return Math.max(0, secs);
    }

    /** Record a failed login. Trips the lockout once failures reach the threshold within the window. */
    public void recordFailure(String email) {
        String k = key(email);
        Instant now = Instant.now();
        attempts.compute(k, (key, existing) -> {
            Attempt a = existing;
            if (a == null || a.firstFailure == null
                    || a.firstFailure.isBefore(now.minus(Duration.ofMinutes(attemptWindowMinutes)))) {
                // start a fresh window
                a = new Attempt();
                a.firstFailure = now;
                a.count = 0;
            }
            a.count++;
            if (a.count >= maxAttempts) {
                a.lockedUntil = now.plus(Duration.ofMinutes(lockMinutes));
                log.warn("Account locked after {} failed logins: {}", a.count, k);
            }
            return a;
        });
    }

    /** Clear the counter after a successful login. */
    public void recordSuccess(String email) {
        attempts.remove(key(email));
    }

    /** Evict entries whose window and any lockout have both elapsed, so the map stays bounded. */
    @Scheduled(fixedRate = 600_000) // every 10 minutes
    void evictStale() {
        Instant cutoffFailure = Instant.now().minus(Duration.ofMinutes(attemptWindowMinutes));
        Instant now = Instant.now();
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            boolean lockExpired = a.lockedUntil == null || a.lockedUntil.isBefore(now);
            boolean windowExpired = a.firstFailure == null || a.firstFailure.isBefore(cutoffFailure);
            return lockExpired && windowExpired;
        });
    }
}
