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
