package com.elcafe.modules.instagram.service;

import com.elcafe.modules.instagram.dto.InstagramBotConfigRequest;
import com.elcafe.modules.instagram.dto.InstagramBotConfigResponse;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramBotConfigService {

    private final InstagramBotConfigRepository configRepository;

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<InstagramBotConfigResponse> getAll() {
        return configRepository.findAll().stream()
                .map(InstagramBotConfigResponse::from)
                .toList();
    }

    public InstagramBotConfigResponse getById(Long id) {
        return InstagramBotConfigResponse.from(findOrThrow(id));
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    @Transactional
    public InstagramBotConfigResponse create(InstagramBotConfigRequest request) {
        // If this config will be active, deactivate others first
        if (Boolean.TRUE.equals(request.getIsActive())) {
            configRepository.findByIsActiveTrue().ifPresent(existing -> {
                existing.setIsActive(false);
                configRepository.save(existing);
                log.info("Deactivated previous active Instagram config id={}", existing.getId());
            });
        }

        InstagramBotConfig config = InstagramBotConfig.builder()
                .appId(request.getAppId())
                .appSecret(blank2null(request.getAppSecret()))
                .accessToken(blank2null(request.getAccessToken()))
                .instagramAccountId(request.getInstagramAccountId())
                .verifyToken(blank2null(request.getVerifyToken()))
                .isActive(Boolean.TRUE.equals(request.getIsActive()))
                .welcomeMessage(request.getWelcomeMessage())
                .autoReplyEnabled(Boolean.TRUE.equals(request.getAutoReplyEnabled()))
                .autoReplyTemplate(request.getAutoReplyTemplate())
                .build();

        configRepository.save(config);
        log.info("Created Instagram config id={}", config.getId());
        return InstagramBotConfigResponse.from(config);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    @Transactional
    public InstagramBotConfigResponse update(Long id, InstagramBotConfigRequest request) {
        InstagramBotConfig config = findOrThrow(id);

        // Activating this config → deactivate others
        if (Boolean.TRUE.equals(request.getIsActive()) && !Boolean.TRUE.equals(config.getIsActive())) {
            configRepository.findByIsActiveTrue().ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    existing.setIsActive(false);
                    configRepository.save(existing);
                }
            });
        }

        if (request.getAppId() != null)             config.setAppId(request.getAppId());
        if (request.getAppSecret() != null)         config.setAppSecret(blank2null(request.getAppSecret()));
        if (request.getAccessToken() != null)       config.setAccessToken(blank2null(request.getAccessToken()));
        if (request.getInstagramAccountId() != null) config.setInstagramAccountId(request.getInstagramAccountId());
        if (request.getVerifyToken() != null)        config.setVerifyToken(blank2null(request.getVerifyToken()));
        if (request.getIsActive() != null)           config.setIsActive(request.getIsActive());
        if (request.getWelcomeMessage() != null)     config.setWelcomeMessage(request.getWelcomeMessage());
        if (request.getAutoReplyEnabled() != null)   config.setAutoReplyEnabled(request.getAutoReplyEnabled());
        if (request.getAutoReplyTemplate() != null)  config.setAutoReplyTemplate(request.getAutoReplyTemplate());

        configRepository.save(config);
        log.info("Updated Instagram config id={}", id);
        return InstagramBotConfigResponse.from(config);
    }

    // -------------------------------------------------------------------------
    // Delete / wipe
    // -------------------------------------------------------------------------

    @Transactional
    public void delete(Long id) {
        InstagramBotConfig config = findOrThrow(id);
        configRepository.delete(config);
        log.info("Deleted Instagram config id={}", id);
    }

    @Transactional
    public InstagramBotConfigResponse clearCredentials(Long id) {
        InstagramBotConfig config = findOrThrow(id);
        config.setAccessToken(null);
        config.setAppSecret(null);
        config.setVerifyToken(null);
        config.setIsActive(false);
        configRepository.save(config);
        log.info("Cleared credentials for Instagram config id={}", id);
        return InstagramBotConfigResponse.from(config);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private InstagramBotConfig findOrThrow(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Instagram config not found: " + id));
    }

    private static String blank2null(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
