package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramSubscriberResponse;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.instagram.service.InstagramBotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Admin view and control of Instagram subscribers.
 *
 *  GET    /api/v1/instagram/subscribers              – paginated list (active)
 *  GET    /api/v1/instagram/subscribers/search       – search by query
 *  GET    /api/v1/instagram/subscribers/{id}         – single subscriber
 *  POST   /api/v1/instagram/subscribers/{id}/block   – block subscriber
 *  POST   /api/v1/instagram/subscribers/{id}/unblock – unblock subscriber
 *  POST   /api/v1/instagram/subscribers/{id}/send    – send DM to subscriber
 *  POST   /api/v1/instagram/subscribers/broadcast    – broadcast to all/registered
 */
@RestController
@RequestMapping("/api/v1/instagram/subscribers")
@RequiredArgsConstructor
public class InstagramSubscriberController {

    private final InstagramSubscriberRepository subscriberRepository;
    private final InstagramBotService botService;

    @GetMapping
    public ResponseEntity<Page<InstagramSubscriberResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                subscriberRepository.findByIsActiveTrue(pageable)
                        .map(InstagramSubscriberResponse::from));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<InstagramSubscriberResponse>> search(
            @RequestParam String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                subscriberRepository.search(q, pageable)
                        .map(InstagramSubscriberResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstagramSubscriberResponse> getById(@PathVariable Long id) {
        return subscriberRepository.findById(id)
                .map(InstagramSubscriberResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<InstagramSubscriberResponse> block(@PathVariable Long id) {
        return ResponseEntity.ok(InstagramSubscriberResponse.from(botService.blockSubscriber(id)));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<InstagramSubscriberResponse> unblock(@PathVariable Long id) {
        return ResponseEntity.ok(InstagramSubscriberResponse.from(botService.unblockSubscriber(id)));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<Map<String, Object>> sendDm(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        boolean ok = botService.sendAdminMessage(id, text);
        return ResponseEntity.ok(Map.of("sent", ok));
    }

    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        String target = body.getOrDefault("target", "ALL"); // ALL | REGISTERED
        int sent = botService.broadcast(text, target);
        return ResponseEntity.ok(Map.of("sent", sent, "target", target));
    }
}
