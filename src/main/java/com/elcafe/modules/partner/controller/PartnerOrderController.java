package com.elcafe.modules.partner.controller;

import com.elcafe.common.ratelimit.RateLimited;
import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.partner.dto.PartnerOrderRequest;
import com.elcafe.modules.partner.dto.PartnerOrderResponse;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.service.PartnerAccessService;
import com.elcafe.modules.partner.service.PartnerOrderPusher;
import com.elcafe.modules.partner.service.PartnerOrderService;
import com.elcafe.security.PartnerPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Order push and status for integrating partners.
 *
 * <p>Both routes are authorized per venue: holding a valid key proves who is calling, never what they
 * may touch. The push additionally requires the order-push capability, which is off by default on a new
 * grant — reading a venue's menu and writing into its kitchen are separate decisions.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/partner/orders")
@RequiredArgsConstructor
@Tag(name = "Partner API", description = "Menu pull and order push for delivery aggregators")
public class PartnerOrderController {

    private final PartnerAccessService partnerAccessService;
    private final PartnerOrderService partnerOrderService;
    private final PartnerOrderPusher partnerOrderPusher;

    @PostMapping
    @RateLimited(type = RateLimited.RateLimitType.PARTNER)
    @Operation(summary = "Push an order",
            description = "Creates an order and prints the kitchen ticket. Idempotent on externalOrderId: "
                    + "a retry returns the original order with duplicate=true. "
                    + "422 = items not on this menu; 409 = sold out, venue closed, or price mismatch.")
    public ResponseEntity<ApiResponse<PartnerOrderResponse>> pushOrder(
            @AuthenticationPrincipal PartnerPrincipal principal,
            @Valid @RequestBody PartnerOrderRequest request) {

        Partner partner = partnerAccessService.requirePartner(principal.getId());
        partnerAccessService.requireOrderAccess(partner.getId(), request.getRestaurantId());

        // Bind the tenant before the write transaction opens, so the §3.4 filter scopes it.
        TenantContext.setRestaurantId(request.getRestaurantId());

        PartnerOrderResponse response = partnerOrderPusher.pushOrder(partner, request);

        // A replay is not a creation. Returning 201 for an order that already existed is what makes a
        // partner's retry logic believe it produced a second one.
        HttpStatus status = Boolean.TRUE.equals(response.getDuplicate())
                ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.success(response));
    }

    @GetMapping("/{externalOrderId}")
    @RateLimited(type = RateLimited.RateLimitType.PARTNER)
    @Operation(summary = "Get one of your orders",
            description = "Current status of an order you pushed, looked up by your own order id. "
                    + "restaurantId is required because order ids are only unique within a venue.")
    public ResponseEntity<ApiResponse<PartnerOrderResponse>> getOrder(
            @AuthenticationPrincipal PartnerPrincipal principal,
            @PathVariable String externalOrderId,
            @RequestParam Long restaurantId) {

        // The venue is re-checked on every read, not just on the push. Revoking a partner has to mean
        // revoking it: without this, a partner that lost venue 11 could still read venue 11's order
        // numbers, line items and prices back through any external id it remembered.
        partnerAccessService.requireOrderAccess(principal.getId(), restaurantId);
        TenantContext.setRestaurantId(restaurantId);

        PartnerOrderResponse response =
                partnerOrderService.getOrder(principal.getId(), restaurantId, externalOrderId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
