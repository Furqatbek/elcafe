package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramSubscriberResponse;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin read-only view of Instagram subscribers.
 *
 *  GET /api/v1/instagram/subscribers          – paginated list (active)
 *  GET /api/v1/instagram/subscribers/search   – search by query (igsid / username / name / phone)
 *  GET /api/v1/instagram/subscribers/{id}     – single subscriber
 */
@RestController
@RequestMapping("/api/v1/instagram/subscribers")
@RequiredArgsConstructor
public class InstagramSubscriberController {

    private final InstagramSubscriberRepository subscriberRepository;

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
}
