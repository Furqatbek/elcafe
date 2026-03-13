package com.elcafe.modules.telegram.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.notification.service.TelegramBotService;
import com.elcafe.modules.telegram.dto.TelegramBotConfigRequest;
import com.elcafe.modules.telegram.dto.TelegramBotConfigResponse;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramBotConfigService {

    private final TelegramBotConfigRepository configRepository;
    @Lazy
    private final TelegramBotService telegramBotService;

    @Transactional(readOnly = true)
    public List<TelegramBotConfigResponse> getAllConfigs() {
        return configRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TelegramBotConfigResponse getConfig(Long id) {
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));
        return toResponse(config);
    }

    @Transactional(readOnly = true)
    public TelegramBotConfigResponse getActiveConfig() {
        TelegramBotConfig config = configRepository.findByIsActiveTrue()
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "isActive", true));
        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse createConfig(TelegramBotConfigRequest request) {
        log.info("Creating Telegram bot config");

        TelegramBotConfig config = TelegramBotConfig.builder()
                .botToken(request.getBotToken())
                .botUsername(request.getBotUsername())
                .webhookUrl(request.getWebhookUrl())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .welcomeMessage(request.getWelcomeMessage())
                .build();

        config = configRepository.save(config);
        log.info("Telegram bot config created with ID: {}", config.getId());

        // Restart bot to apply new configuration
        telegramBotService.restartBot();

        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse updateConfig(Long id, TelegramBotConfigRequest request) {
        log.info("Updating Telegram bot config: {}", id);

        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));

        // Always overwrite token/username so they can be cleared by sending an empty string.
        // A null in the request means "leave unchanged" (field not sent by the client).
        if (request.getBotToken() != null) {
            config.setBotToken(request.getBotToken().isBlank() ? null : request.getBotToken());
        }
        if (request.getBotUsername() != null) {
            config.setBotUsername(request.getBotUsername().isBlank() ? null : request.getBotUsername());
        }
        if (request.getWebhookUrl() != null) {
            config.setWebhookUrl(request.getWebhookUrl());
        }
        if (request.getIsActive() != null) {
            config.setIsActive(request.getIsActive());
        }
        if (request.getWelcomeMessage() != null) {
            config.setWelcomeMessage(request.getWelcomeMessage());
        }

        config = configRepository.save(config);
        log.info("Telegram bot config updated: {}", id);

        // Restart bot to apply new configuration
        telegramBotService.restartBot();

        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse toggleConfig(Long id) {
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));

        config.setIsActive(!Boolean.TRUE.equals(config.getIsActive()));
        config = configRepository.save(config);
        log.info("Telegram bot config {} toggled to: {}", id, config.getIsActive());

        // Restart bot to apply new configuration
        telegramBotService.restartBot();

        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse clearCredentials(Long id) {
        log.info("Clearing Telegram bot credentials: {}", id);
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));

        config.setBotToken(null);
        config.setBotUsername(null);
        config.setIsActive(false);
        config = configRepository.save(config);
        log.info("Telegram bot credentials cleared for config: {}", id);

        // Stop the bot since credentials are gone
        telegramBotService.stopBot();

        return toResponse(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        log.info("Deleting Telegram bot config: {}", id);
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));
        configRepository.delete(config);
        log.info("Telegram bot config deleted: {}", id);

        // Restart bot (will stop if no active config remains)
        telegramBotService.restartBot();
    }

    private TelegramBotConfigResponse toResponse(TelegramBotConfig config) {
        return TelegramBotConfigResponse.builder()
                .id(config.getId())
                .botUsername(config.getBotUsername())
                .webhookUrl(config.getWebhookUrl())
                .isActive(config.getIsActive())
                .welcomeMessage(config.getWelcomeMessage())
                .hasToken(StringUtils.hasText(config.getBotToken()))
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
