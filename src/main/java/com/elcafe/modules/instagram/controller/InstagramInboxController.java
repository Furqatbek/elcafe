package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramConversationResponse;
import com.elcafe.modules.instagram.dto.InstagramConversationSummaryResponse;
import com.elcafe.modules.instagram.dto.InstagramSendResult;
import com.elcafe.modules.instagram.dto.InstagramSubscriberResponse;
import com.elcafe.modules.instagram.service.InstagramInboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * The agent-takeover Instagram inbox (V179): durable conversation history plus human handoff, on top
 * of the inbound-message storage {@code InstagramWebhookService} now performs on every DM.
 *
 *  GET  /api/v1/instagram/inbox                     – recent conversations, newest inbound first
 *  GET  /api/v1/instagram/inbox/{subscriberId}       – one conversation's merged, chronological history
 *  POST /api/v1/instagram/inbox/{subscriberId}/reply – agent reply (reuses InstagramBotService#sendAdminMessage)
 *  POST /api/v1/instagram/inbox/{subscriberId}/takeover – claim the thread from the wizard
 *  POST /api/v1/instagram/inbox/{subscriberId}/release  – hand it back to the wizard
 *
 * <p>Same shape as {@link InstagramSubscriberController}: the role gate stays ADMIN/OWNER/MANAGER — the
 * people who already manage this restaurant's Instagram presence — with the tenant boundary enforced
 * underneath by {@link InstagramInboxService} (a foreign subscriber id reads as not-found). Pinned by
 * {@code RbacGateAnnotationTest}'s {@code TENANT_ROLE_GATED} list.
 */
@RestController
@RequestMapping("/api/v1/instagram/inbox")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class InstagramInboxController {

    private final InstagramInboxService inboxService;

    @GetMapping
    public ResponseEntity<Page<InstagramConversationSummaryResponse>> listConversations(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(inboxService.listConversations(pageable));
    }

    @GetMapping("/{subscriberId}")
    public ResponseEntity<InstagramConversationResponse> getConversation(@PathVariable Long subscriberId) {
        return ResponseEntity.ok(inboxService.getConversation(subscriberId));
    }

    /** Mirrors {@code InstagramSubscriberController#sendDm}'s response shape (same {@link
     *  InstagramSendResult} underneath), so the reason for a failed send is never collapsed to a bare
     *  boolean here either. */
    @PostMapping("/{subscriberId}/reply")
    public ResponseEntity<Map<String, Object>> reply(
            @PathVariable Long subscriberId,
            @RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        InstagramSendResult result = inboxService.reply(subscriberId, text);

        Map<String, Object> response = new HashMap<>();
        response.put("sent", result.delivered());
        if (!result.delivered()) {
            response.put("reason", result.failure().name());
            response.put("retryable", result.failure().retryable());
            if (result.message() != null) {
                response.put("providerMessage", result.message());
            }
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Claim the subscriber's thread away from the wizard. An optional {@code {"hours": N}} body
     * overrides the configured default duration for this one take-over; omitted/absent body and a
     * missing/non-positive {@code hours} both fall back to it (see {@code
     * InstagramInboxService#takeover}).
     */
    @PostMapping("/{subscriberId}/takeover")
    public ResponseEntity<InstagramSubscriberResponse> takeover(
            @PathVariable Long subscriberId,
            @RequestBody(required = false) Map<String, Object> body) {
        Long hours = body != null ? asLong(body.get("hours")) : null;
        return ResponseEntity.ok(inboxService.takeover(subscriberId, hours));
    }

    @PostMapping("/{subscriberId}/release")
    public ResponseEntity<InstagramSubscriberResponse> release(@PathVariable Long subscriberId) {
        return ResponseEntity.ok(inboxService.release(subscriberId));
    }

    /** {@code body.get("hours")} may arrive as a JSON number or a numeric string — mirrors {@code
     *  InstagramSubscriberController#asLong} exactly, for the same reason. */
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
