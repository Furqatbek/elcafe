package com.elcafe.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression guard for the RBAC remediation (audit): the codebase has no method-security integration
 * harness, so these controllers' role gates are otherwise untested. This asserts, by reflection, that
 * the gates I added stay in place — a silent removal (which would re-open "any authenticated token
 * reaches this staff/POS endpoint") fails the build.
 */
class RbacGateAnnotationTest {

    /** Controllers that were unprotected and now require a class-level @PreAuthorize. */
    private static final List<String> CLASS_GATED = List.of(
            "com.elcafe.modules.pos.giftcard.controller.GiftCardController",
            "com.elcafe.modules.pos.tax.controller.TaxExemptionController",
            "com.elcafe.modules.pos.offline.controller.OfflineSyncController",
            "com.elcafe.modules.pos.barcode.controller.BarcodeController",
            "com.elcafe.modules.inventory.controller.SupplierController",
            "com.elcafe.modules.customer.controller.CustomerActivityController",
            "com.elcafe.modules.instagram.controller.InstagramBotConfigController",
            "com.elcafe.modules.instagram.controller.InstagramSubscriberController");

    @Test
    @DisplayName("previously-unprotected staff/POS controllers carry a class-level @PreAuthorize")
    void staffControllersAreGated() throws Exception {
        for (String fqcn : CLASS_GATED) {
            Class<?> c = Class.forName(fqcn);
            assertThat(c.isAnnotationPresent(PreAuthorize.class))
                    .as("%s must carry a class-level @PreAuthorize (RBAC gate)", fqcn)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("public self-registration is gated to SUPER_ADMIN")
    void registerRequiresSuperAdmin() throws Exception {
        Class<?> auth = Class.forName("com.elcafe.modules.auth.controller.AuthController");
        Method register = Arrays.stream(auth.getDeclaredMethods())
                .filter(m -> m.getName().equals("register"))
                .findFirst().orElseThrow();
        PreAuthorize pa = register.getAnnotation(PreAuthorize.class);
        assertThat(pa).as("AuthController.register must be @PreAuthorize-gated").isNotNull();
        assertThat(pa.value()).contains("SUPER_ADMIN");
    }

    /**
     * Endpoints that stay SUPER_ADMIN-only because they operate PLATFORM infrastructure rather than
     * one restaurant's data. {@code SmsController} is the raw send/balance//token surface of the
     * shared Eskiz account: the credentials, the sender ID and the prepaid balance belong to the
     * platform, so a tenant must reach SMS through its own campaigns, never by driving the broker
     * directly.
     */
    private static final List<String> SUPER_ADMIN_ONLY = List.of(
            "com.elcafe.modules.sms.controller.SmsController");

    /**
     * Per-tenant channels. These must stay gated to a tenant-scoped role set — never anonymous, and
     * never widened to a role that has no restaurant (which is what would silently un-scope them).
     */
    private static final List<String> TENANT_ROLE_GATED = List.of(
            "com.elcafe.modules.sms.controller.SmsCampaignController",
            "com.elcafe.modules.sms.controller.SmsTemplateController",
            "com.elcafe.modules.sms.controller.SmsAutomationController",
            "com.elcafe.modules.sms.controller.SmsLogController",
            "com.elcafe.modules.telegram.controller.TelegramCampaignController",
            "com.elcafe.modules.telegram.controller.TelegramSubscriberController",
            "com.elcafe.modules.telegram.controller.TelegramTemplateController",
            "com.elcafe.modules.telegram.controller.TelegramBotConfigController",
            "com.elcafe.modules.instagram.controller.InstagramBotConfigController",
            "com.elcafe.modules.instagram.controller.InstagramSubscriberController",
            "com.elcafe.modules.instagram.controller.InstagramCampaignController",
            "com.elcafe.modules.instagram.controller.InstagramTemplateController",
            // V179: the agent-takeover Instagram inbox — same per-restaurant staff roles as every other
            // Instagram admin surface above; appended here rather than inline-sorted so a parallel
            // agent's edit to this list stays a clean merge.
            "com.elcafe.modules.instagram.controller.InstagramInboxController",
            // V178 (automation-rules/scheduler agent): birthday/win-back automation rules — same
            // per-tenant-channel gate as every other Instagram controller above.
            "com.elcafe.modules.instagram.controller.InstagramAutomationController");

    @Test
    @DisplayName("platform-infrastructure controllers stay locked to SUPER_ADMIN")
    void marketingControllersAreSuperAdminOnly() throws Exception {
        for (String fqcn : SUPER_ADMIN_ONLY) {
            Class<?> c = Class.forName(fqcn);
            PreAuthorize pa = c.getAnnotation(PreAuthorize.class);
            assertThat(pa).as("%s must carry a class-level @PreAuthorize", fqcn).isNotNull();
            assertThat(pa.value())
                    .as("%s must be gated to SUPER_ADMIN (platform-operated, shared infra)", fqcn)
                    .contains("SUPER_ADMIN");
        }
    }

    @Test
    @DisplayName("per-tenant channel controllers are gated to tenant-scoped roles")
    void perTenantChannelsAreTenantRoleGated() throws Exception {
        for (String fqcn : TENANT_ROLE_GATED) {
            Class<?> c = Class.forName(fqcn);
            PreAuthorize pa = c.getAnnotation(PreAuthorize.class);
            assertThat(pa).as("%s must carry a class-level @PreAuthorize", fqcn).isNotNull();
            assertThat(pa.value())
                    .as("%s manages one restaurant's own channel, so it must be gated to that "
                            + "restaurant's staff roles", fqcn)
                    .contains("ADMIN")
                    .contains("OWNER")
                    .contains("MANAGER");
        }
    }
}
