package com.elcafe.modules.settings.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.settings.dto.PrintAgentStatusResponse;
import com.elcafe.modules.settings.service.PrintAgentStatusService;
import com.elcafe.security.JwtUtil;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mints the long-lived token a headless print agent presents on the WebSocket CONNECT (audit #21). The
 * token is always scoped to the CALLER's own restaurant (from the principal, never a client value), so a
 * tenant admin cannot mint a token for another tenant. Paste the token into the agent's {@code .env}
 * ({@code AGENT_TOKEN}). Note: minting a new token does not revoke a prior one (stateless, no per-token
 * version) — see {@link JwtUtil#generatePrintAgentToken}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings/print-agent")
@RequiredArgsConstructor
public class PrintAgentTokenController {

    private final JwtUtil jwtUtil;
    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final PrintAgentStatusService printAgentStatusService;

    @PostMapping("/token")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> mintToken() {
        Long restaurantId = restaurantAuthorizationService.getCurrentUserRestaurantId();
        if (restaurantId == null) {
            throw new AccessDeniedException("Only a restaurant-scoped admin can mint a print-agent token");
        }
        String token = jwtUtil.generatePrintAgentToken(restaurantId);
        log.info("Minted a print-agent token for restaurant {}", restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Print-agent token minted",
                Map.of("token", token, "restaurantId", restaurantId)));
    }

    /**
     * Whether this venue's kitchen tickets are reaching a printer.
     *
     * <p>Readable by an operator as well as an admin: the person who notices that tickets stopped
     * coming is standing at the pass, not looking at a settings screen.
     *
     * <p>Unlike the mint above it takes a venue, because an admin over several venues needs to see each
     * one and a status is a read. {@code checkAccessIfPresent} is what keeps that from being a way to
     * read another tenant's; omitting it falls back to the caller's own venue.
     */
    @GetMapping("/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'OPERATOR')")
    public ResponseEntity<ApiResponse<PrintAgentStatusResponse>> status(
            @RequestParam(required = false) Long restaurantId) {
        restaurantAuthorizationService.checkAccessIfPresent(restaurantId);
        Long venue = restaurantId != null
                ? restaurantId
                : restaurantAuthorizationService.getCurrentUserRestaurantId();
        if (venue == null) {
            throw new AccessDeniedException("Name a restaurant to read print-agent status for");
        }
        return ResponseEntity.ok(ApiResponse.success(printAgentStatusService.statusFor(venue)));
    }
}
