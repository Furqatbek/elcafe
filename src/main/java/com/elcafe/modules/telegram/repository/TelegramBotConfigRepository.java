package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramBotConfigRepository extends JpaRepository<TelegramBotConfig, Long> {

    /**
     * Every active configuration, one per restaurant (V164: at most one each, enforced by
     * {@code uq_tg_config_active_per_restaurant}).
     *
     * <p>Was {@code Optional} when Telegram was a single global bot. It is now a List because the
     * bot launcher must start one bot per restaurant; called at boot with no TenantContext bound so
     * the restaurantFilter stays off and it genuinely sees every tenant. Within a tenant-bound
     * request the filter narrows it to that restaurant's own config.
     */
    List<TelegramBotConfig> findByIsActiveTrue();

    /** The active configuration of one restaurant, if it has one. */
    Optional<TelegramBotConfig> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    /** All configurations belonging to one restaurant. */
    List<TelegramBotConfig> findByRestaurantIdOrderByIdAsc(Long restaurantId);

    Optional<TelegramBotConfig> findByBotUsername(String botUsername);
}
