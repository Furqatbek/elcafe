package com.elcafe.modules.instagram.controller;

import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
import com.elcafe.modules.instagram.service.InstagramBotConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

/**
 * REST API for managing Instagram bot configuration.
 *
 *  GET    /api/v1/instagram/config          – list all configs
 *  GET    /api/v1/instagram/config/{id}     – get one config
 *  POST   /api/v1/instagram/config          – create config
 *  PUT    /api/v1/instagram/config/{id}     – update config
 *  DELETE /api/v1/instagram/config/{id}     – delete config
 *  DELETE /api/v1/instagram/config/{id}/credentials – wipe tokens
 */
@RestController
@RequestMapping("/api/v1/instagram/config")
@RequiredArgsConstructor
// V163: Instagram is a per-tenant channel — each restaurant connects its own Meta app + IG business
// account, so its own ADMIN/OWNER/MANAGER manage it. The tenant boundary is enforced underneath, not
// by keeping everyone out: the §3.4 restaurantFilter plus explicit scoping in InstagramBotConfigService
// (every read and write resolves the caller's restaurant; a config id from another tenant reads as
// not-found, never overwritten). Same shape as InstagramSubscriberController; the role set itself is
// pinned by RbacGateAnnotationTest's TENANT_ROLE_GATED list.
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER')")
public class InstagramBotConfigController {

    private final InstagramBotConfigService configService;

    @GetMapping
    public ResponseEntity<List<InstagramBotConfigResponse>> getAll() {
        return ResponseEntity.ok(configService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstagramBotConfigResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getById(id));
    }

    @PostMapping
    public ResponseEntity<InstagramBotConfigResponse> create(@RequestBody InstagramBotConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InstagramBotConfigResponse> update(
            @PathVariable Long id,
            @RequestBody InstagramBotConfigRequest request) {
        return ResponseEntity.ok(configService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/credentials")
    public ResponseEntity<InstagramBotConfigResponse> clearCredentials(@PathVariable Long id) {
        return ResponseEntity.ok(configService.clearCredentials(id));
    }
}
