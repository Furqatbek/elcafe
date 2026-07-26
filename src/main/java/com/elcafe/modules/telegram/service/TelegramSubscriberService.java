package com.elcafe.modules.telegram.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.telegram.dto.TelegramSubscriberResponse;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramCampaignRecipientRepository;
import com.elcafe.modules.telegram.repository.TelegramLogRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberLocationRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramSubscriberService {

    private final TelegramSubscriberRepository subscriberRepository;
    private final ShiftTimeService shiftTimeService;
    private final RestaurantRepository restaurantRepository;
    private final TelegramSubscriberLocationRepository locationRepository;
    private final TelegramLogRepository logRepository;
    private final TelegramCampaignRecipientRepository campaignRecipientRepository;

    @Transactional(readOnly = true)
    public Page<TelegramSubscriberResponse> getAllSubscribers(Pageable pageable) {
        return subscriberRepository.findAll(pageable).map(TelegramSubscriberResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<TelegramSubscriberResponse> getActiveSubscribers(Pageable pageable) {
        return subscriberRepository.findByIsActiveTrue(pageable).map(TelegramSubscriberResponse::from);
    }

    @Transactional(readOnly = true)
    public TelegramSubscriberResponse getSubscriberById(Long id) {
        TelegramSubscriber subscriber = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramSubscriber", "id", id));
        return TelegramSubscriberResponse.from(subscriber);
    }

    @Transactional(readOnly = true)
    public TelegramSubscriberResponse getSubscriberByTelegramUserId(Long telegramUserId) {
        TelegramSubscriber subscriber = subscriberRepository.findByTelegramUserId(telegramUserId)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramSubscriber", "telegramUserId", telegramUserId));
        return TelegramSubscriberResponse.from(subscriber);
    }

    @Transactional
    public TelegramSubscriberResponse createOrUpdateSubscriber(Long telegramUserId, String username,
            String firstName, String lastName, String languageCode) {
        log.info("Creating/updating Telegram subscriber: {}", telegramUserId);

        TelegramSubscriber subscriber = subscriberRepository.findByTelegramUserId(telegramUserId)
                .orElse(TelegramSubscriber.builder()
                        .telegramUserId(telegramUserId)
                        .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .build());

        subscriber.setUsername(username);
        subscriber.setFirstName(firstName);
        subscriber.setLastName(lastName);
        subscriber.setLanguageCode(languageCode);
        subscriber.setIsActive(true);
        subscriber.updateLastInteraction();

        subscriber = subscriberRepository.save(subscriber);
        log.info("Telegram subscriber saved: {}", subscriber.getId());
        return TelegramSubscriberResponse.from(subscriber);
    }

    @Transactional
    public TelegramSubscriberResponse blockSubscriber(Long id) {
        TelegramSubscriber subscriber = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramSubscriber", "id", id));
        subscriber.setIsBlocked(true);
        subscriber = subscriberRepository.save(subscriber);
        log.info("Telegram subscriber blocked: {}", id);
        return TelegramSubscriberResponse.from(subscriber);
    }

    @Transactional
    public TelegramSubscriberResponse unblockSubscriber(Long id) {
        TelegramSubscriber subscriber = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramSubscriber", "id", id));
        subscriber.setIsBlocked(false);
        subscriber = subscriberRepository.save(subscriber);
        log.info("Telegram subscriber unblocked: {}", id);
        return TelegramSubscriberResponse.from(subscriber);
    }

    /**
     * Erase one subscriber: their PII (name, username, phone, birthday) and every child row that holds
     * more of it — saved locations, message logs, campaign-recipient records. The last two have
     * {@code ON DELETE RESTRICT} FKs (V64), so they must go first or the subscriber delete is refused.
     * Scoped by the §3.4 restaurantFilter like {@code blockSubscriber} above (request-bound), so another
     * tenant's id reads as not-found. Blocking only greyed the row out; this is the erasure path.
     */
    @Transactional
    public void deleteSubscriber(Long id) {
        TelegramSubscriber subscriber = subscriberRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TelegramSubscriber", "id", id));
        eraseSubscriber(subscriber);
    }

    /**
     * Erase the Telegram subscribers linked to a customer being deleted. Their {@code customer_id} FK is
     * {@code ON DELETE RESTRICT} (V64) — deleting the customer would otherwise FAIL outright — so purging
     * the subscribers (children first) here, inside the customer's delete transaction and before the
     * customer row is removed, both erases the PII and unblocks the delete.
     */
    @EventListener
    @Transactional
    public void onCustomerDeleted(CustomerDeletedEvent event) {
        subscriberRepository.findAllByCustomerId(event.customerId()).forEach(this::eraseSubscriber);
    }

    private void eraseSubscriber(TelegramSubscriber subscriber) {
        campaignRecipientRepository.deleteBySubscriber(subscriber);
        logRepository.deleteBySubscriber(subscriber);
        locationRepository.deleteBySubscriber(subscriber);
        subscriberRepository.delete(subscriber);
        log.info("Erased Telegram subscriber {} (restaurant {})",
                subscriber.getId(), subscriber.getRestaurantId());
    }

    @Transactional(readOnly = true)
    public Page<TelegramSubscriberResponse> searchSubscribers(String query, Pageable pageable) {
        return subscriberRepository.searchSubscribers(query, pageable).map(TelegramSubscriberResponse::from);
    }

    @Transactional(readOnly = true)
    public List<TelegramSubscriber> getActiveSubscribersForCampaign() {
        return subscriberRepository.findByIsActiveTrueAndIsBlockedFalse();
    }

    @Transactional(readOnly = true)
    public List<TelegramSubscriber> getInactiveSubscribers(int daysInactive) {
        Long restaurantId = getPrimaryRestaurantId();
        LocalDate currentBusinessDay = shiftTimeService.getCurrentBusinessDay(restaurantId);
        LocalDate cutoffDate = currentBusinessDay.minusDays(daysInactive);
        ShiftTimeService.ShiftTimeRange shiftRange = shiftTimeService.getShiftTimeRange(
                restaurantId, cutoffDate);
        return subscriberRepository.findInactiveSubscribers(shiftRange.start());
    }

    @Transactional(readOnly = true)
    public List<TelegramSubscriber> getLinkedCustomerSubscribers() {
        return subscriberRepository.findByCustomerIdIsNotNull();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics() {
        // Get primary restaurant ID for shift-aware calculations
        Long restaurantId = getPrimaryRestaurantId();
        LocalDate currentBusinessDay = shiftTimeService.getCurrentBusinessDay(restaurantId);

        // Calculate shift-aware date ranges
        LocalDate weekAgo = currentBusinessDay.minusWeeks(1);
        LocalDate monthAgo = currentBusinessDay.minusMonths(1);

        ShiftTimeService.ShiftTimeRange weekRange = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, weekAgo, currentBusinessDay);
        ShiftTimeService.ShiftTimeRange monthRange = shiftTimeService.getShiftTimeRangeForPeriod(
                restaurantId, monthAgo, currentBusinessDay);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSubscribers", subscriberRepository.count());
        stats.put("activeSubscribers", subscriberRepository.countActiveSubscribers());
        stats.put("newThisWeek", subscriberRepository.countNewSubscribersSince(weekRange.start()));
        stats.put("newThisMonth", subscriberRepository.countNewSubscribersSince(monthRange.start()));
        stats.put("currentBusinessDay", currentBusinessDay);
        return stats;
    }

    /**
     * Get the primary restaurant ID for shift-aware calculations.
     * Uses the first active restaurant's business hours.
     * Returns null if no active restaurants exist (will use calendar dates as fallback).
     */
    private Long getPrimaryRestaurantId() {
        List<Restaurant> activeRestaurants = restaurantRepository.findByActiveTrue();
        if (activeRestaurants.isEmpty()) {
            log.debug("No active restaurants found, using calendar dates for statistics");
            return null;
        }
        return activeRestaurants.get(0).getId();
    }
}
