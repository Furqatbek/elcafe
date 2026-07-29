package com.elcafe.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-fast guardrail for the {@code prod} profile. The base {@code application.yml} ships
 * developer-friendly (fail-OPEN) defaults so local dev is frictionless; {@code application-prod.yml}
 * overrides them. This validator is the backstop that refuses to start production if any unsafe setting
 * is still active — whether because the prod profile override was removed, or an env var re-enabled it.
 *
 * <p>Only active under the {@code prod} profile, so it never affects local dev or the test suite.
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionConfigValidator {

    @Value("${app.consumer.otp.development-mode:false}")
    private boolean otpDevelopmentMode;

    @Value("${app.consumer.otp.include-in-response:false}")
    private boolean otpIncludeInResponse;

    @Value("${app.security.cors.allowed-origins:}")
    private String corsAllowedOrigins;

    @Value("${app.security.cors.allow-credentials:false}")
    private boolean corsAllowCredentials;

    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerEnabled;

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean apiDocsEnabled;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @PostConstruct
    void validate() {
        List<String> errors = new ArrayList<>();

        if (otpDevelopmentMode) {
            errors.add("app.consumer.otp.development-mode must be FALSE in prod — dev mode accepts ANY OTP "
                    + "code (consumer account takeover). Unset CONSUMER_OTP_DEVELOPMENT_MODE or set it false.");
        }
        if (otpIncludeInResponse) {
            errors.add("app.consumer.otp.include-in-response must be FALSE in prod — it returns the OTP to "
                    + "the caller.");
        }
        if (corsAllowCredentials && isWildcardOrBlank(corsAllowedOrigins)) {
            errors.add("CORS is misconfigured: allow-credentials is true with blank or wildcard origins. "
                    + "A wildcard origin with credentials lets any site make authenticated cross-origin "
                    + "calls. Set CORS_ORIGINS to an explicit comma-separated origin list.");
        }
        if (swaggerEnabled || apiDocsEnabled) {
            errors.add("springdoc swagger-ui/api-docs must be disabled in prod — they disclose the full API "
                    + "surface to anonymous users.");
        }
        // The base application.yml carries `password: ${DB_PASSWORD:postgres}` so local dev works out of
        // the box. Shipping that default to production is a publicly-known database credential.
        if (datasourcePassword == null || datasourcePassword.isBlank()
                || "postgres".equals(datasourcePassword)) {
            errors.add("spring.datasource.password is blank or the well-known 'postgres' default — set "
                    + "DB_PASSWORD to a real secret in prod.");
        }

        if (!errors.isEmpty()) {
            String message = "Refusing to start with the 'prod' profile — unsafe configuration:\n  - "
                    + String.join("\n  - ", errors);
            log.error(message);
            throw new IllegalStateException(message);
        }
        log.info("[prod] configuration validated: OTP dev-mode off, CORS origins explicit, API docs disabled.");
    }

    private static boolean isWildcardOrBlank(String origins) {
        if (origins == null || origins.isBlank()) {
            return true;
        }
        for (String o : origins.split(",")) {
            if ("*".equals(o.trim())) {
                return true;
            }
        }
        return false;
    }
}
