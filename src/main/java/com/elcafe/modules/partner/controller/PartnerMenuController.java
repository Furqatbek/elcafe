package com.elcafe.modules.partner.controller;

import com.elcafe.common.ratelimit.RateLimited;
import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.partner.dto.PartnerMenuResponse;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.partner.service.PartnerMenuService;
import com.elcafe.security.PartnerPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Menu pull for integrating partners. Authenticated by {@code X-Partner-Key} and authorized per venue.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/partner/menu")
@RequiredArgsConstructor
@Tag(name = "Partner API", description = "Menu pull and order push for delivery aggregators")
public class PartnerMenuController {

    private final PartnerAccessService partnerAccessService;
    private final PartnerMenuService partnerMenuService;

    @GetMapping("/{restaurantId}")
    @RateLimited(type = RateLimited.RateLimitType.PARTNER)
    @Operation(summary = "Get a venue's menu",
            description = "Full menu including variants, add-on groups and availability flags. "
                    + "Requires an active grant with menu-read for this restaurant.")
    public ResponseEntity<ApiResponse<PartnerMenuResponse>> getMenu(
            @AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable Long restaurantId) {

        partnerAccessService.requireMenuAccess(principal.getId(), restaurantId);

        // Bind the tenant BEFORE the read opens its transaction, so the §3.4 restaurantFilter is
        // enabled on it. Setting it inside the service would be too late — the transaction manager
        // reads TenantContext at transaction begin. TenantEnforcementFilter clears it after the request.
        TenantContext.setRestaurantId(restaurantId);

        PartnerMenuResponse menu = partnerMenuService.getMenu(restaurantId);
        log.debug("Partner {} pulled menu for restaurant {}", principal.getSlug(), restaurantId);
        return ResponseEntity.ok(ApiResponse.success(menu));
    }
}
