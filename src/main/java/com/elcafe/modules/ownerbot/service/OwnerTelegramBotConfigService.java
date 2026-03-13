package com.elcafe.modules.ownerbot.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.ownerbot.dto.OwnerBotConfigRequest;
import com.elcafe.modules.ownerbot.dto.OwnerBotConfigResponse;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramBotConfig;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramBotConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerTelegramBotConfigService {

    private final OwnerTelegramBotConfigRepository configRepository;

    @Transactional(readOnly = true)
    public List<OwnerBotConfigResponse> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public OwnerBotConfigResponse getConfig(Long id) {
        OwnerTelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerTelegramBotConfig", "id", id));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public OwnerBotConfigResponse getConfigByRestaurant(Long restaurantId) {
        OwnerTelegramBotConfig config = configRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerTelegramBotConfig", "restaurantId", restaurantId));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public OwnerBotConfigResponse getActiveConfig() {
        OwnerTelegramBotConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("OwnerTelegramBotConfig", "isActive", true));
        return toResponse(config);
    }

    @Transactional
    public OwnerBotConfigResponse createConfig(OwnerBotConfigRequest request, Long restaurantId) {
        log.info("Creating Owner Telegram bot config for restaurant: {}", restaurantId);

        OwnerTelegramBotConfig config = OwnerTelegramBotConfig.builder()
                .restaurantId(restaurantId)
                .botToken(request.getBotToken())
                .botUsername(request.getBotUsername())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .welcomeMessage(request.getWelcomeMessage())
                .autoVerifyOwners(request.getAutoVerifyOwners() != null ? request.getAutoVerifyOwners() : true)
                .build();

        config = configRepository.save(config);
        log.info("Owner Telegram bot config created with ID: {}", config.getId());
        return toResponse(config);
    }

    @Transactional
    public OwnerBotConfigResponse updateConfig(Long id, OwnerBotConfigRequest request) {
        log.info("Updating Owner Telegram bot config: {}", id);

        OwnerTelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerTelegramBotConfig", "id", id));

        if (StringUtils.hasText(request.getBotToken())) {
            config.setBotToken(request.getBotToken());
        }
        if (StringUtils.hasText(request.getBotUsername())) {
            config.setBotUsername(request.getBotUsername());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }
        if (request.getWelcomeMessage() != null) {
            config.setWelcomeMessage(request.getWelcomeMessage());
        }
        if (request.getAutoVerifyOwners() != null) {
            config.setAutoVerifyOwners(request.getAutoVerifyOwners());
        }

        config = configRepository.save(config);
        log.info("Owner Telegram bot config updated: {}", id);
        return toResponse(config);
    }

    @Transactional
    public OwnerBotConfigResponse toggleConfig(Long id) {
        OwnerTelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerTelegramBotConfig", "id", id));

        config.setIsActive(!Boolean.TRUE.equals(config.getIsActive()));
        config = configRepository.save(config);
        log.info("Owner Telegram bot config {} toggled to: {}", id, config.getIsActive());
        return toResponse(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        log.info("Deleting Owner Telegram bot config: {}", id);
        OwnerTelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("OwnerTelegramBotConfig", "id", id));
        configRepository.delete(config);
        log.info("Owner Telegram bot config deleted: {}", id);
    }

    private OwnerBotConfigResponse toResponse(OwnerTelegramBotConfig config) {
        return OwnerBotConfigResponse.builder()
                .id(config.getId())
                .restaurantId(config.getRestaurantId())
                .botUsername(config.getBotUsername())
                .isActive(config.getIsActive())
                .welcomeMessage(config.getWelcomeMessage())
                .autoVerifyOwners(config.getAutoVerifyOwners())
                .hasToken(StringUtils.hasText(config.getBotToken()))
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
