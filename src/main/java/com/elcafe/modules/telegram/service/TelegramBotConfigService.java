package com.elcafe.modules.telegram.service;

import com.elcafe.common.channel.ChannelWriteGuard;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
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
    private final RestaurantAuthorizationService restaurantAuthorizationService;
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
        Long restaurantId = requireWritableTenant();
        TelegramBotConfig config = configRepository.findByRestaurantIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "isActive", true));
        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse createConfig(TelegramBotConfigRequest request) {
        Long restaurantId = requireWritableTenant();
        log.info("Creating Telegram bot config for restaurant {}", restaurantId);

        boolean activating = request.getIsActive() == null || request.getIsActive();
        if (activating) {
            deactivateActiveConfig(restaurantId, null);
        }

        TelegramBotConfig config = TelegramBotConfig.builder()
                .restaurantId(restaurantId)
                .botToken(request.getBotToken())
                .botUsername(request.getBotUsername())
                .webhookUrl(request.getWebhookUrl())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .welcomeMessage(request.getWelcomeMessage())
                .build();

        config = configRepository.save(config);
        log.info("Telegram bot config created with ID: {}", config.getId());

        // Restart only this restaurant's bot
        telegramBotService.restartBot(restaurantId);

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

        // Restart only this restaurant's bot
        telegramBotService.restartBot(config.getRestaurantId());

        return toResponse(config);
    }

    @Transactional
    public TelegramBotConfigResponse toggleConfig(Long id) {
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));

        boolean turningOn = !Boolean.TRUE.equals(config.getIsActive());
        if (turningOn) {
            // uq_tg_config_active_per_restaurant allows only one active config per restaurant.
            deactivateActiveConfig(config.getRestaurantId(), config.getId());
        }
        config.setIsActive(turningOn);
        config = configRepository.save(config);
        log.info("Telegram bot config {} toggled to: {}", id, config.getIsActive());

        // Restart only this restaurant's bot
        telegramBotService.restartBot(config.getRestaurantId());

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

        // Stop only this restaurant's bot — other tenants keep running
        telegramBotService.stopBot(config.getRestaurantId());

        return toResponse(config);
    }

    @Transactional
    public void deleteConfig(Long id) {
        log.info("Deleting Telegram bot config: {}", id);
        TelegramBotConfig config = configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramBotConfig", "id", id));
        Long restaurantId = config.getRestaurantId();
        configRepository.delete(config);
        log.info("Telegram bot config deleted: {}", id);

        // Restart this restaurant's bot (stops it when no active config remains)
        telegramBotService.restartBot(restaurantId);
    }

    /** Step the tenant's currently-active config down, so at most one stays active per restaurant. */
    private void deactivateActiveConfig(Long restaurantId, Long exceptId) {
        configRepository.findByRestaurantIdAndIsActiveTrue(restaurantId).ifPresent(existing -> {
            if (exceptId != null && exceptId.equals(existing.getId())) {
                return;
            }
            existing.setIsActive(false);
            // Flush before the new row claims the flag — the partial unique index is checked per
            // statement.
            configRepository.saveAndFlush(existing);
            log.info("Deactivated previous active Telegram config id={} for restaurant {}",
                    existing.getId(), restaurantId);
        });
    }

    /**
     * The restaurant a bot configuration belongs to. V164 made Telegram a per-tenant channel: each
     * restaurant runs its own bot, so a platform account has no bot of its own to configure.
     */
    private Long requireWritableTenant() {
        return ChannelWriteGuard.requireRestaurant(restaurantAuthorizationService.currentTenantScopeStrict(), "A Telegram bot", "to configure one");
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
