package com.elcafe.modules.settings.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.security.JwtUtil;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mints the long-lived token a headless print agent presents on the WebSocket CONNECT (audit #21). The
 * token is always scoped to the CALLER's own restaurant (from the principal, never a client value), so a
 * tenant admin cannot mint a token for another tenant. Paste the token into the agent's {@code .env}
 * ({@code AGENT_TOKEN}); re-mint to rotate.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/settings/print-agent")
@RequiredArgsConstructor
public class PrintAgentTokenController {

    private final JwtUtil jwtUtil;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

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
}
