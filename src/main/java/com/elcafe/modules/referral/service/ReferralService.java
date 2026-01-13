package com.elcafe.modules.referral.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.loyalty.service.BonusService;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.marketing.event.MarketingEventPublisher;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.referral.dto.*;
import com.elcafe.modules.referral.entity.Referral;
import com.elcafe.modules.referral.entity.ReferralCode;
import com.elcafe.modules.referral.entity.ReferralSettings;
import com.elcafe.modules.referral.enums.ReferralStatus;
import com.elcafe.modules.referral.enums.RewardType;
import com.elcafe.modules.referral.repository.ReferralCodeRepository;
import com.elcafe.modules.referral.repository.ReferralRepository;
import com.elcafe.modules.referral.repository.ReferralSettingsRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referralRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralSettingsRepository referralSettingsRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final LoyaltyService loyaltyService;
    private final BonusService bonusService;
    private final CustomerLoyaltyRepository customerLoyaltyRepository;

    @Autowired
    @Lazy
    private MarketingEventPublisher marketingEventPublisher;

    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ==================== Settings Management ====================

    /**
     * Get referral settings for a restaurant
     */
    @Transactional(readOnly = true)
    public ReferralSettingsResponse getSettings(Long restaurantId) {
        ReferralSettings settings = referralSettingsRepository.findByRestaurantId(restaurantId)
                .orElse(null);

        if (settings == null) {
            return null;
        }

        return ReferralSettingsResponse.from(settings);
    }

    /**
     * Create or update referral settings for a restaurant
     */
    @Transactional
    public ReferralSettingsResponse saveSettings(Long restaurantId, ReferralSettingsRequest request) {
        log.info("Saving referral settings for restaurant: {}", restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant not found"));

        ReferralSettings settings = referralSettingsRepository.findByRestaurantId(restaurantId)
                .orElse(ReferralSettings.builder()
                        .restaurant(restaurant)
                        .build());

        settings.setProgramActive(request.getProgramActive() != null ? request.getProgramActive() : false);
        settings.setReferrerRewardType(request.getReferrerRewardType());
        settings.setReferrerRewardAmount(request.getReferrerRewardAmount());
        settings.setRefereeRewardType(request.getRefereeRewardType());
        settings.setRefereeRewardAmount(request.getRefereeRewardAmount());
        settings.setMinOrderAmount(request.getMinOrderAmount());
        settings.setMaxReferralsPerCustomer(request.getMaxReferralsPerCustomer());
        settings.setRewardExpiresDays(request.getRewardExpiresDays() != null ? request.getRewardExpiresDays() : 30);
        settings.setTermsAndConditions(request.getTermsAndConditions());

        settings = referralSettingsRepository.save(settings);
        log.info("Referral settings saved for restaurant: {}", restaurantId);

        return ReferralSettingsResponse.from(settings);
    }

    /**
     * Toggle referral program active status
     */
    @Transactional
    public ReferralSettingsResponse toggleProgram(Long restaurantId) {
        ReferralSettings settings = referralSettingsRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Referral settings not found for restaurant"));

        settings.setProgramActive(!Boolean.TRUE.equals(settings.getProgramActive()));
        settings = referralSettingsRepository.save(settings);
        log.info("Referral program toggled to {} for restaurant: {}", settings.getProgramActive(), restaurantId);

        return ReferralSettingsResponse.from(settings);
    }

    // ==================== Referral Code Management ====================

    /**
     * Generate a referral code for a customer
     */
    @Transactional
    public ReferralCodeResponse generateReferralCode(Long restaurantId, Long customerId) {
        log.info("Generating referral code for customer {} at restaurant {}", customerId, restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant not found"));

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        // Check if customer already has a code for this restaurant
        Optional<ReferralCode> existing = referralCodeRepository.findByRestaurantIdAndCustomerId(restaurantId, customerId);
        if (existing.isPresent()) {
            return ReferralCodeResponse.from(existing.get());
        }

        // Generate unique code
        String code = generateUniqueCode();

        ReferralCode referralCode = ReferralCode.builder()
                .restaurant(restaurant)
                .customer(customer)
                .code(code)
                .active(true)
                .usageCount(0)
                .build();

        referralCode = referralCodeRepository.save(referralCode);
        log.info("Referral code {} generated for customer {}", code, customerId);

        return ReferralCodeResponse.from(referralCode);
    }

    /**
     * Get a customer's referral code
     */
    @Transactional(readOnly = true)
    public ReferralCodeResponse getCustomerReferralCode(Long restaurantId, Long customerId) {
        ReferralCode referralCode = referralCodeRepository.findByRestaurantIdAndCustomerId(restaurantId, customerId)
                .orElse(null);

        if (referralCode == null) {
            return null;
        }

        return ReferralCodeResponse.from(referralCode);
    }

    /**
     * Get all referral codes for a restaurant (admin)
     */
    @Transactional(readOnly = true)
    public Page<ReferralCodeResponse> getReferralCodes(Long restaurantId, Pageable pageable) {
        return referralCodeRepository.findByRestaurantId(restaurantId, pageable)
                .map(ReferralCodeResponse::from);
    }

    /**
     * Validate a referral code
     */
    @Transactional(readOnly = true)
    public boolean validateReferralCode(String code, Long restaurantId) {
        Optional<ReferralCode> referralCode = referralCodeRepository.findByCode(code);

        if (referralCode.isEmpty()) {
            return false;
        }

        ReferralCode rc = referralCode.get();
        return rc.getRestaurant().getId().equals(restaurantId) && rc.isValid();
    }

    // ==================== Referral Processing ====================

    /**
     * Process a referral using only the code (auto-detect restaurant from code)
     * Used when creating customers from admin panel without restaurant context
     */
    @Transactional
    public ReferralResponse processReferralByCode(String code, Long newCustomerId) {
        log.info("Processing referral with code {} for customer {} (auto-detect restaurant)", code, newCustomerId);

        // Find referral code first to get restaurant
        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Invalid referral code"));

        Long restaurantId = referralCode.getRestaurant().getId();
        return processReferral(restaurantId, code, newCustomerId);
    }

    /**
     * Process a referral when a new customer uses a referral code
     */
    @Transactional
    public ReferralResponse processReferral(Long restaurantId, String code, Long newCustomerId) {
        log.info("Processing referral with code {} for customer {} at restaurant {}", code, newCustomerId, restaurantId);

        // Validate referral settings are active
        ReferralSettings settings = referralSettingsRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new BadRequestException("Referral program not available"));

        if (!Boolean.TRUE.equals(settings.getProgramActive())) {
            throw new BadRequestException("Referral program is not active");
        }

        // Find and validate referral code
        ReferralCode referralCode = referralCodeRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("Invalid referral code"));

        if (!referralCode.getRestaurant().getId().equals(restaurantId)) {
            throw new BadRequestException("Invalid referral code for this restaurant");
        }

        if (!referralCode.isValid()) {
            throw new BadRequestException("Referral code is no longer valid");
        }

        // Check if customer was already referred
        if (referralRepository.existsByRefereeIdAndRestaurantId(newCustomerId, restaurantId)) {
            throw new BadRequestException("You have already been referred to this restaurant");
        }

        // Prevent self-referral
        if (referralCode.getCustomer().getId().equals(newCustomerId)) {
            throw new BadRequestException("You cannot use your own referral code");
        }

        // Check max referrals per customer
        if (settings.getMaxReferralsPerCustomer() != null) {
            long referralCount = referralRepository.countByReferrerAndRestaurantAndStatus(
                    referralCode.getCustomer().getId(), restaurantId, ReferralStatus.COMPLETED);
            if (referralCount >= settings.getMaxReferralsPerCustomer()) {
                throw new BadRequestException("Referrer has reached maximum referral limit");
            }
        }

        Customer referee = customerRepository.findById(newCustomerId)
                .orElseThrow(() -> new EntityNotFoundException("Customer not found"));

        // Create pending referral
        Referral referral = Referral.builder()
                .restaurant(referralCode.getRestaurant())
                .referralCode(referralCode)
                .referrer(referralCode.getCustomer())
                .referee(referee)
                .status(ReferralStatus.PENDING)
                .build();

        referral = referralRepository.save(referral);

        // Increment usage count
        referralCode.incrementUsage();
        referralCodeRepository.save(referralCode);

        log.info("Referral {} created for referee {} by referrer {}",
                referral.getId(), newCustomerId, referralCode.getCustomer().getId());

        // Grant referee reward immediately
        grantRefereeReward(referral, settings);

        return ReferralResponse.from(referral);
    }

    /**
     * Complete a referral when referee makes their first qualifying order
     */
    @Transactional
    public void completeReferral(Order order) {
        Long refereeId = order.getCustomer().getId();
        Long restaurantId = order.getRestaurant().getId();

        log.info("Checking for pending referral for customer {} at restaurant {}", refereeId, restaurantId);

        Optional<Referral> referralOpt = referralRepository.findByRefereeIdAndRestaurantId(refereeId, restaurantId);

        if (referralOpt.isEmpty()) {
            log.debug("No pending referral found for customer {}", refereeId);
            return;
        }

        Referral referral = referralOpt.get();

        if (referral.getStatus() != ReferralStatus.PENDING) {
            log.debug("Referral {} already processed with status {}", referral.getId(), referral.getStatus());
            return;
        }

        ReferralSettings settings = referralSettingsRepository.findByRestaurantId(restaurantId).orElse(null);
        if (settings == null || !Boolean.TRUE.equals(settings.getProgramActive())) {
            log.debug("Referral program not active for restaurant {}", restaurantId);
            return;
        }

        // Check minimum order amount
        if (settings.getMinOrderAmount() != null &&
            order.getTotal().compareTo(settings.getMinOrderAmount()) < 0) {
            log.debug("Order amount {} below minimum {} for referral completion",
                    order.getTotal(), settings.getMinOrderAmount());
            return;
        }

        // Complete the referral
        referral.complete(order);

        // Grant referrer reward
        grantReferrerReward(referral, settings);

        referralRepository.save(referral);
        log.info("Referral {} completed for order {}", referral.getId(), order.getId());

        // Publish referral completed event for marketing automation
        if (marketingEventPublisher != null) {
            marketingEventPublisher.publishReferralCompleted(
                    referral.getReferrer(),
                    referral.getReferee(),
                    settings.getReferrerRewardAmount(),
                    settings.getRefereeRewardAmount()
            );
        }
    }

    /**
     * Grant referee reward (called when referral is created)
     */
    private void grantRefereeReward(Referral referral, ReferralSettings settings) {
        if (Boolean.TRUE.equals(referral.getRefereeRewardGiven())) {
            return;
        }

        RewardType rewardType = settings.getRefereeRewardType();
        BigDecimal rewardAmount = settings.getRefereeRewardAmount();

        if (rewardType == RewardType.BONUS_POINTS) {
            grantBonusPointsReward(referral.getReferee().getId(), rewardAmount,
                    "Referral signup bonus", "referee-bonus-" + referral.getId());
        }
        // Other reward types (discount, free item) would create coupons/vouchers

        referral.grantRefereeReward(rewardType, rewardAmount);
        referralRepository.save(referral);

        log.info("Referee reward granted: {} {} to customer {}",
                rewardAmount, rewardType, referral.getReferee().getId());
    }

    /**
     * Grant referrer reward (called when referral is completed)
     */
    private void grantReferrerReward(Referral referral, ReferralSettings settings) {
        if (Boolean.TRUE.equals(referral.getReferrerRewardGiven())) {
            return;
        }

        RewardType rewardType = settings.getReferrerRewardType();
        BigDecimal rewardAmount = settings.getReferrerRewardAmount();

        if (rewardType == RewardType.BONUS_POINTS) {
            grantBonusPointsReward(referral.getReferrer().getId(), rewardAmount,
                    "Referral reward for inviting " + referral.getReferee().getFirstName() + " " + referral.getReferee().getLastName(),
                    "referrer-bonus-" + referral.getId());
        }
        // Other reward types (discount, free item) would create coupons/vouchers

        referral.grantReferrerReward(rewardType, rewardAmount);
        referralRepository.save(referral);

        log.info("Referrer reward granted: {} {} to customer {}",
                rewardAmount, rewardType, referral.getReferrer().getId());
    }

    /**
     * Grant bonus points reward via loyalty system
     */
    private void grantBonusPointsReward(Long customerId, BigDecimal amount, String description, String idempotencyKey) {
        CustomerLoyalty loyalty = loyaltyService.getOrCreateCustomerLoyalty(customerId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "referral");
        metadata.put("customerId", customerId);

        bonusService.recordTransaction(
                loyalty,
                BonusTransaction.TransactionType.REFERRAL_BONUS,
                amount,
                null,
                description,
                idempotencyKey,
                metadata
        );

        customerLoyaltyRepository.save(loyalty);
    }

    // ==================== Referral Tracking ====================

    /**
     * Get all referrals for a restaurant (admin)
     */
    @Transactional(readOnly = true)
    public Page<ReferralResponse> getReferrals(Long restaurantId, Pageable pageable) {
        return referralRepository.findByRestaurantIdWithDetails(restaurantId, pageable)
                .map(ReferralResponse::from);
    }

    /**
     * Get referrals made by a specific customer
     */
    @Transactional(readOnly = true)
    public List<ReferralResponse> getCustomerReferrals(Long restaurantId, Long customerId) {
        return referralRepository.findByReferrerAndRestaurant(customerId, restaurantId).stream()
                .map(ReferralResponse::from)
                .toList();
    }

    /**
     * Get referral statistics for a restaurant
     */
    @Transactional(readOnly = true)
    public ReferralStatsResponse getStats(Long restaurantId) {
        long totalReferrals = referralRepository.countByRestaurantId(restaurantId);
        long pendingReferrals = referralRepository.countByRestaurantIdAndStatus(restaurantId, ReferralStatus.PENDING);
        long completedReferrals = referralRepository.countByRestaurantIdAndStatus(restaurantId, ReferralStatus.COMPLETED);
        long activeReferralCodes = referralCodeRepository.countActiveByRestaurantId(restaurantId);

        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        long referralsThisMonth = referralRepository.countByRestaurantIdAndCreatedAtAfter(restaurantId, monthStart);

        // Get top referrer
        List<ReferralCode> topReferrers = referralCodeRepository.findTopReferrersByRestaurant(
                restaurantId, PageRequest.of(0, 1));

        ReferralStatsResponse.ReferralStatsResponseBuilder builder = ReferralStatsResponse.builder()
                .totalReferrals(totalReferrals)
                .pendingReferrals(pendingReferrals)
                .completedReferrals(completedReferrals)
                .activeReferralCodes(activeReferralCodes)
                .referralsThisMonth(referralsThisMonth);

        if (!topReferrers.isEmpty()) {
            ReferralCode topReferrer = topReferrers.get(0);
            builder.topReferrerId(topReferrer.getCustomer().getId())
                   .topReferrerName(topReferrer.getCustomer().getFirstName() + " " + topReferrer.getCustomer().getLastName())
                   .topReferrerCount(topReferrer.getUsageCount());
        }

        return builder.build();
    }

    // ==================== Utility Methods ====================

    /**
     * Generate a unique referral code
     */
    private String generateUniqueCode() {
        String code;
        int attempts = 0;
        do {
            code = generateRandomCode();
            attempts++;
            if (attempts > 100) {
                throw new RuntimeException("Failed to generate unique referral code");
            }
        } while (referralCodeRepository.existsByCode(code));
        return code;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARACTERS.charAt(RANDOM.nextInt(CODE_CHARACTERS.length())));
        }
        return sb.toString();
    }

    /**
     * Expire pending referrals that have passed the expiry threshold
     */
    @Transactional
    public void expirePendingReferrals(int expiryDays) {
        LocalDateTime expiryDate = LocalDateTime.now().minusDays(expiryDays);
        List<Referral> expiredReferrals = referralRepository.findExpiredPendingReferrals(expiryDate);

        for (Referral referral : expiredReferrals) {
            referral.setStatus(ReferralStatus.EXPIRED);
            referralRepository.save(referral);
            log.info("Referral {} expired", referral.getId());
        }

        if (!expiredReferrals.isEmpty()) {
            log.info("Expired {} pending referrals older than {} days", expiredReferrals.size(), expiryDays);
        }
    }
}
