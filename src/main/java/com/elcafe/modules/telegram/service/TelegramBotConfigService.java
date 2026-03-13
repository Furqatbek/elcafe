package com.elcafe.modules.telegram.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.telegram.dto.TelegramBotConfigRequest;
import com.elcafe.modules.telegram.dto.TelegramBotConfigResponse;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
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
public class TelegramBotConfigService {

    private final TelegramBotConfigRepository configRepository;

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
        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse updateConfig(Long id, TelegramBotConfigRequest request) {
        log.info("Updating Telegram bot config: {}", id);

        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));

        if (StringUtils.hasText(request.getBotToken())) {
            config.setBotToken(request.getBotToken());
        }
        if (StringUtils.hasText(request.getBotUsername())) {
            config.setBotUsername(request.getBotUsername());
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
        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse toggleConfig(Long id) {
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));

        config.setIsActive(!Boolean.TRUE.equals(config.getIsActive()));
        config = configRepository.save(config);
        log.info("Telegram bot config {} toggled to: {}", id, config.getIsActive());
        return toResponse(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        log.info("Deleting Telegram bot config: {}", id);
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));
        configRepository.delete(config);
        log.info("Telegram bot config deleted: {}", id);
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
