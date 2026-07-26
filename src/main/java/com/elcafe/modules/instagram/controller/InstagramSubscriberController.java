package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramCampaignRequest;
import com.elcafe.modules.instagram.dto.InstagramCampaignResponse;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.dto.InstagramStatisticsResponse;
import com.elcafe.modules.instagram.dto.InstagramSubscriberResponse;
import com.elcafe.modules.instagram.service.InstagramBotService;
import com.elcafe.modules.instagram.service.InstagramCampaignService;
import com.elcafe.modules.instagram.service.InstagramStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin view and control of Instagram subscribers.
 *
 *  GET    /api/v1/instagram/subscribers              – paginated list (active)
 *  GET    /api/v1/instagram/subscribers/search       – search by query
 *  GET    /api/v1/instagram/subscribers/statistics   – tenant-scoped subscriber + message-log stats
 *  GET    /api/v1/instagram/subscribers/{id}         – single subscriber
 *  POST   /api/v1/instagram/subscribers/{id}/block   – block subscriber
 *  POST   /api/v1/instagram/subscribers/{id}/unblock – unblock subscriber
 *  POST   /api/v1/instagram/subscribers/{id}/link    – link subscriber to a customer (same restaurant)
 *  POST   /api/v1/instagram/subscribers/{id}/unlink  – clear subscriber's customer link
 *  POST   /api/v1/instagram/subscribers/{id}/send    – send DM to subscriber
 *  POST   /api/v1/instagram/subscribers/broadcast    – broadcast to all/registered
 *  DELETE /api/v1/instagram/subscribers/{id}         – erase subscriber + PII (ADMIN/OWNER)
 */
/*
 * V163: Instagram is a per-tenant channel. The role gate below stays ADMIN/OWNER/MANAGER — those are
 * exactly the people who should manage their OWN restaurant's Instagram presence — and the tenant
 * boundary is enforced underneath it: every read and write in InstagramBotService is scoped to the
 * caller's restaurant, so a guessed id from another tenant reads as not-found.
 */
@RestController
@RequestMapping("/api/v1/instagram/subscribers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class InstagramSubscriberController {

    private final InstagramBotService botService;
    private final InstagramCampaignService campaignService;
    private final InstagramStatisticsService statisticsService;

    @GetMapping
    public ResponseEntity<Page<InstagramSubscriberResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                botService.listSubscribers(pageable).map(InstagramSubscriberResponse::from));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<InstagramSubscriberResponse>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                botService.searchSubscribers(q, pageable).map(InstagramSubscriberResponse::from));
    }

    /**
     * Tenant-scoped subscriber and message-log statistics for the operator dashboard. Requires a
     * restaurant-scoped caller: a platform (SUPER_ADMIN) account has no per-tenant statistics of its
     * own to show, so it gets a 400 rather than an all-zero or cross-tenant-aggregate response — see
     * {@code InstagramStatisticsService#requireTenant}.
     */
    @GetMapping("/statistics")
    public ResponseEntity<InstagramStatisticsResponse> getStatistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstagramSubscriberResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(InstagramSubscriberResponse.from(botService.getSubscriber(id)));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<InstagramSubscriberResponse> block(@PathVariable Long id) {
        return ResponseEntity.ok(InstagramSubscriberResponse.from(botService.blockSubscriber(id)));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<InstagramSubscriberResponse> unblock(@PathVariable Long id) {
        return ResponseEntity.ok(InstagramSubscriberResponse.from(botService.unblockSubscriber(id)));
    }

    /**
     * Manually link a subscriber to an existing customer of the SAME restaurant — the escape hatch for
     * when the wizard's phone-based auto-link ({@code InstagramBotService#completeRegistration}) did not
     * fire: a phone typo, a customer created after the subscriber registered, or a format its
     * canonicalizer could not resolve. Tenant-scoped underneath ({@code
     * InstagramBotService#linkSubscriberToCustomer}): a customer id from another restaurant is rejected
     * (404) exactly like a foreign subscriber id; a missing/unparseable {@code customerId} is a 400.
     */
    @PostMapping("/{id}/link")
    public ResponseEntity<InstagramSubscriberResponse> link(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Long customerId = asLong(body.get("customerId"));
        if (customerId == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(
                InstagramSubscriberResponse.from(botService.linkSubscriberToCustomer(id, customerId)));
    }

    /** Clear a subscriber's customer link — the undo for {@link #link}. Tenant-scoped like every other
     *  admin action on this controller. */
    @PostMapping("/{id}/unlink")
    public ResponseEntity<InstagramSubscriberResponse> unlink(@PathVariable Long id) {
        return ResponseEntity.ok(
                InstagramSubscriberResponse.from(botService.unlinkSubscriberFromCustomer(id)));
    }

    /**
     * Erase a subscriber and all their PII (name, phone, birth date, saved addresses). Destructive and
     * irreversible, so it is tightened to ADMIN/OWNER above the class default — a manager blocks, an
     * owner erases. Tenant-scoped underneath: another restaurant's id reads as not-found.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OWNER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        botService.deleteSubscriber(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Map<String, Object>> sendDm(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        InstagramSendResult result = botService.sendAdminMessage(id, text);

        Map<String, Object> response = new HashMap<>();
        response.put("sent", result.delivered());
        if (!result.delivered()) {
            // Tell the operator WHY. The old bare {"sent": false} made a dead access token and a
            // blocked recipient indistinguishable, so the UI could only ever show a generic failure.
            response.put("reason", result.failure().name());
            response.put("retryable", result.failure().retryable());
            if (result.message() != null) {
                response.put("providerMessage", result.message());
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Broadcast to all/registered subscribers. Backed by the async campaign engine (V166): this creates
     * a campaign, starts it on a background thread, and returns 202 with the campaign's id/status so the
     * caller polls {@code GET /campaigns/{id}}. The old synchronous loop blocked the request thread and
     * 504'd past the proxy timeout, and — recording nothing — double-sent on a retry.
     */
    @PostMapping("/broadcast")
    public ResponseEntity<InstagramCampaignResponse> broadcast(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        InstagramCampaignRequest request = new InstagramCampaignRequest();
        request.setMessageText(text);
        request.setTargetAudience(body.getOrDefault("target", "ALL")); // ALL | REGISTERED
        request.setName(body.get("name"));
        request.setImageUrl(body.get("imageUrl"));   // V176: optional promo photo; blank/absent → text-only
        return ResponseEntity.accepted().body(campaignService.createAndSend(request));
    }

    /**
     * {@code body.get("customerId")} may arrive as a JSON number (Integer/Long, the natural shape for
     * {@code {"customerId": 42}}) or as a numeric string — {@link Map}'s value type here is {@code
     * Object} (unlike this controller's other endpoints' {@code Map<String, String>} bodies) precisely
     * so both survive Jackson deserialization intact; this tolerates whichever the caller sent rather
     * than demanding one specific JSON type for a single numeric field.
     */
    private static Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
