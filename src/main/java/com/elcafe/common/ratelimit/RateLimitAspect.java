package com.elcafe.common.ratelimit;

import com.elcafe.config.RateLimitConfig;
import com.elcafe.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

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
}
