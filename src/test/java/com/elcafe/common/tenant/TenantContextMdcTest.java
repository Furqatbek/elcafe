package com.elcafe.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the MDC mirror of {@link TenantContext}: every log line emitted while a request is
 * tenant-scoped must carry {@code tenantId} (rendered by the JSON log format and available to plain
 * patterns as {@code %X{tenantId}}), and the key must follow the holder's lifecycle exactly so it can
 * never leak onto a pooled thread that {@code clear()}ed.
 */
class TenantContextMdcTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("setRestaurantId mirrors the id into the tenantId MDC key")
    void setMirrorsIntoMdc() {
        TenantContext.setRestaurantId(7L);
        assertThat(MDC.get(TenantContext.MDC_TENANT_KEY)).isEqualTo("7");
    }

    @Test
    @DisplayName("re-scoping to null (unscoped request) removes the key instead of logging 'null'")
    void nullScopeRemovesKey() {
        TenantContext.setRestaurantId(7L);
        TenantContext.setRestaurantId(null);
        assertThat(MDC.get(TenantContext.MDC_TENANT_KEY)).isNull();
    }

    @Test
    @DisplayName("clear() removes the key together with the thread-locals")
    void clearRemovesKey() {
        TenantContext.setRestaurantId(9L);
        TenantContext.clear();
        assertThat(MDC.get(TenantContext.MDC_TENANT_KEY)).isNull();
    }
}
