package com.elcafe.modules.partner.zbr;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * How to reach ZBR.
 *
 * <p>Unset by default, which switches the dispatcher off entirely. That is deliberate rather than
 * lazy: a partner with no dispatcher has no events queued for it at all, so an environment that has
 * not been given a key does not accumulate a backlog of messages it can never deliver.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.partner.zbr")
public class ZbrProperties {

    /**
     * Their base URL — {@code https://zbrr.uz}, or {@code https://staging.zbrr.uz}.
     *
     * <p>Which one you point at is decided by which key you hold, not by this value alone: their keys
     * are environment-scoped and a staging key is refused in production. The one integration mistake
     * whose cost lands outside both companies is a test order printing in a real kitchen, and they
     * have built for that; pointing a production key at staging simply fails.
     */
    private String baseUrl = "";

    /** The partner key, sent as {@code X-Partner-Key}. Blank disables the dispatcher. */
    private String apiKey = "";

    /** Their own client uses 5s; they suggested we assume the same of them. */
    private int connectTimeoutMs = 5000;

    /** Their suggestion again — 10s. A partner's slowness must not become our backlog. */
    private int readTimeoutMs = 10000;

    /**
     * Items per bulk menu call. Their limit is 1000.
     *
     * <p>Left well under it: a request carrying a thousand rows is one that times out slowly and
     * retries expensively, and their partial-success response means a smaller call loses less when
     * something in it is wrong.
     */
    private int bulkBatchSize = 200;

    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
