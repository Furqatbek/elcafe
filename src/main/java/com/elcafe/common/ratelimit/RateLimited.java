package com.elcafe.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to apply rate limiting to controller methods.
 * Uses Bucket4j for token bucket rate limiting.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    /**
     * The type of rate limiting to apply.
     */
    RateLimitType type() default RateLimitType.USER;

    /**
     * Name of the endpoint for EXPENSIVE_ENDPOINT type limiting.
     * If not specified, uses method name.
     */
    String endpointName() default "";

    enum RateLimitType {
        /**
         * Per-user rate limiting (100 requests/minute)
         */
        USER,

        /**
         * Per-user analytics rate limiting (20 requests/minute)
         */
        ANALYTICS,

        /**
         * Global expensive endpoint limiting (10 requests/minute)
         */
        EXPENSIVE_ENDPOINT,

        /**
         * Per-IP limiting for unauthenticated auth endpoints (login / PIN / OTP / password reset).
         * Strict (10 requests/minute per IP) — brute-force defense. Keyed by client IP, not username,
         * because the caller is anonymous at this point.
         */
        AUTH,

        /**
         * Per-partner limiting for the V187 partner API (600 requests/minute). Keyed by the partner
         * slug, so one aggregator polling hard cannot crowd out another. Deliberately far looser than
         * USER: a partner legitimately pulls menus for many venues and pushes orders in bursts, and
         * the key is an issued credential we can revoke rather than an anonymous caller.
         */
        PARTNER
    }
}
