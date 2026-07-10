package com.elcafe.common.ratelimit;

import com.elcafe.config.RateLimitConfig;
import com.elcafe.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Aspect that enforces rate limiting on methods annotated with @RateLimited.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimitConfig rateLimitConfig;

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String username = getCurrentUsername();
        boolean allowed;

        switch (rateLimited.type()) {
            case USER:
                allowed = rateLimitConfig.getUserBucket(username).tryConsume(1);
                break;

            case ANALYTICS:
                allowed = rateLimitConfig.tryConsumeAnalytics(username);
                break;

            case EXPENSIVE_ENDPOINT:
                String endpointName = getEndpointName(joinPoint, rateLimited);
                // Check both user-level and endpoint-level limits
                allowed = rateLimitConfig.tryConsumeAnalytics(username) &&
                          rateLimitConfig.tryConsumeExpensiveEndpoint(endpointName);
                break;

            case AUTH:
                // Unauthenticated endpoint — key by client IP + endpoint, not username.
                String authKey = getEndpointName(joinPoint, rateLimited) + ":" + getClientIp();
                allowed = rateLimitConfig.tryConsumeAuth(authKey);
                break;

            default:
                allowed = true;
        }

        if (!allowed) {
            long remainingTokens = rateLimitConfig.getRemainingAnalyticsTokens(username);
            log.warn("Rate limit exceeded for user {} on {} (type: {}, remaining: {})",
                     username, joinPoint.getSignature().getName(), rateLimited.type(), remainingTokens);
            throw new RateLimitExceededException(
                    "Rate limit exceeded. Please wait before making more requests.");
        }

        return joinPoint.proceed();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return "anonymous";
    }

    private String getEndpointName(ProceedingJoinPoint joinPoint, RateLimited rateLimited) {
        if (!rateLimited.endpointName().isEmpty()) {
            return rateLimited.endpointName();
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /**
     * Client IP for rate-limit keying. The app runs behind nginx, which sets {@code X-Real-IP} to the
     * real TCP peer ({@code $remote_addr}) and OVERWRITES any client-supplied value — so it is
     * spoof-resistant, unlike {@code X-Forwarded-For} whose leading entries are attacker-controlled (a
     * brute-forcer could otherwise rotate XFF to dodge the limit). Prefer X-Real-IP; fall back to the
     * last XFF hop (nginx's view of the client), then the socket address.
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "unknown";
            }
            HttpServletRequest request = attrs.getRequest();

            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // last hop was appended by the trusted proxy; leading hops are client-spoofable
                String[] hops = forwarded.split(",");
                return hops[hops.length - 1].trim();
            }
            String remote = request.getRemoteAddr();
            return remote != null ? remote : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
