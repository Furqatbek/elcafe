package com.elcafe.modules.partner.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.partner.dto.CreatePartnerRequest;
import com.elcafe.modules.partner.dto.PartnerAdminResponse;
import com.elcafe.modules.partner.dto.PartnerGrantRequest;
import com.elcafe.modules.partner.dto.PartnerKeyResponse;
import com.elcafe.modules.partner.dto.PartnerPriceRuleRequest;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.IntegrationEvent;
import com.elcafe.modules.partner.entity.PartnerPriceRule;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.IntegrationEventStatus;
import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import com.elcafe.modules.partner.repository.IntegrationEventRepository;
import com.elcafe.modules.partner.repository.PartnerPriceRuleRepository;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Staff-side management of integration partners: who exists, what key they hold, which venues. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerAdminService {

    private final PartnerRepository partnerRepository;
    private final PartnerRestaurantRepository partnerRestaurantRepository;
    private final PartnerPriceRuleRepository partnerPriceRuleRepository;
    private final com.elcafe.modules.partner.outbox.PartnerMenuNotifier partnerMenuNotifier;
    private final IntegrationEventRepository integrationEventRepository;
    private final RestaurantRepository restaurantRepository;
    private final com.elcafe.modules.menu.repository.CategoryRepository categoryRepository;
    private final com.elcafe.modules.menu.repository.ProductRepository productRepository;
    private final com.elcafe.modules.menu.repository.ProductVariantRepository productVariantRepository;
    private final PartnerAccessService partnerAccessService;

    @Transactional(readOnly = true)
    public List<PartnerAdminResponse> listPartners() {
        return partnerRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Creates the partner and returns its key — the only moment the raw key exists outside the
     * partner's own configuration.
     */
    @Transactional
    public PartnerKeyResponse createPartner(CreatePartnerRequest request) {
        if (partnerRepository.existsBySlug(request.getSlug())) {
            throw new BadRequestException("A partner with this slug already exists: " + request.getSlug());
        }

        String rawKey = partnerAccessService.generateApiKey();

        Partner partner = partnerRepository.save(Partner.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .apiKeyHash(partnerAccessService.hashApiKey(rawKey))
                .apiKeyPrefix(partnerAccessService.prefixOf(rawKey))
                .contactEmail(request.getContactEmail())
                .customerFeePercent(request.getCustomerFeePercent() == null
                        ? BigDecimal.ZERO : request.getCustomerFeePercent())
                .active(true)
                .build());

        log.info("Created integration partner {} (slug={})", partner.getId(), partner.getSlug());
        return PartnerKeyResponse.builder()
                .partnerId(partner.getId())
                .name(partner.getName())
                .slug(partner.getSlug())
                .apiKey(rawKey)
                .build();
    }

    /**
     * Issues a new key and invalidates the old one immediately. There is deliberately no grace period
     * where both work: rotation is what you reach for when a key may have leaked, and a window in which
     * the leaked key still opens the door is the one thing rotation must not leave you with.
     */
    @Transactional
    public PartnerKeyResponse rotateKey(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        String rawKey = partnerAccessService.generateApiKey();
        partner.setApiKeyHash(partnerAccessService.hashApiKey(rawKey));
        partner.setApiKeyPrefix(partnerAccessService.prefixOf(rawKey));
        partnerRepository.save(partner);

        log.warn("Rotated API key for partner {} (slug={}) — the previous key is now dead",
                partner.getId(), partner.getSlug());
        return PartnerKeyResponse.builder()
                .partnerId(partner.getId())
                .name(partner.getName())
                .slug(partner.getSlug())
                .apiKey(rawKey)
                .build();
    }

    /**
     * Deactivating is the kill switch: authentication filters on {@code active}, so every venue goes
     * dark at once without touching the grants, and reactivating restores exactly what was there.
     */
    @Transactional
    public PartnerAdminResponse setActive(Long partnerId, boolean active) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));
        partner.setActive(active);
        partnerRepository.save(partner);
        log.info("Partner {} (slug={}) set active={}", partner.getId(), partner.getSlug(), active);
        return toResponse(partner);
    }

    /**
     * Record what this partner adds at their own checkout.
     *
     * <p>Its own endpoint rather than a field on the grant, because the charge is the partner's and
     * applies wherever they operate — a venue does not negotiate it. Nothing downstream prices with
     * it; it reaches the admin screen and stops there.
     */
    @Transactional
    public PartnerAdminResponse setCustomerFee(Long partnerId, BigDecimal customerFeePercent) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        BigDecimal fee = customerFeePercent == null ? BigDecimal.ZERO : customerFeePercent;
        if (fee.compareTo(BigDecimal.ZERO) < 0 || fee.compareTo(new BigDecimal("100")) > 0) {
            throw new BadRequestException("A partner fee must be between 0 and 100%");
        }

        partner.setCustomerFeePercent(fee);
        partnerRepository.save(partner);
        log.info("Partner {} (slug={}) customer fee set to {}%",
                partner.getId(), partner.getSlug(), fee);
        return toResponse(partner);
    }

    /**
     * Record whether this partner's {@code paymentMode} reflects how the customer actually paid.
     *
     * <p>Turning it <b>off</b> also withdraws order push everywhere, which is the point: a partner who
     * tells us the field has stopped being trustworthy — an app rolled back, a venue that turns out to
     * take cash — should not keep pushing orders while somebody remembers to revoke the grants one by
     * one. Menu access is left alone; it never carried money.
     */
    @Transactional
    public PartnerAdminResponse setPaymentModeConfirmed(Long partnerId, boolean confirmed) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        partner.setPaymentModeConfirmed(confirmed);
        partnerRepository.save(partner);

        if (!confirmed) {
            List<PartnerRestaurant> withdrawn = partnerRestaurantRepository.findByPartnerId(partnerId)
                    .stream()
                    .filter(grant -> Boolean.TRUE.equals(grant.getCanPushOrders()))
                    .peek(grant -> grant.setCanPushOrders(false))
                    .toList();
            partnerRestaurantRepository.saveAll(withdrawn);
            if (!withdrawn.isEmpty()) {
                log.warn("Partner {} paymentMode unconfirmed — order push withdrawn at {} venue(s)",
                        partner.getSlug(), withdrawn.size());
            }
        }

        log.info("Partner {} (slug={}) paymentMode confirmed={}",
                partner.getId(), partner.getSlug(), confirmed);
        return toResponse(partner);
    }

    /** Grant or update a partner's access to one venue. Idempotent — re-granting updates in place. */
    @Transactional
    public PartnerAdminResponse grantRestaurant(Long partnerId, Long restaurantId, PartnerGrantRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        PartnerRestaurant grant = partnerRestaurantRepository
                .findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .orElseGet(() -> PartnerRestaurant.builder()
                        .partnerId(partnerId)
                        .restaurantId(restaurantId)
                        .build());

        // Order push is the only capability that moves money, and it trusts the partner's paymentMode:
        // PREPAID creates the payment settled and prints a paid ticket. A partner who has not told us
        // the field is real can hand a venue's food to a courier who owes nothing, once per cash order.
        // Refused here rather than remembered, because our own demo seeder had already forgotten it.
        if (Boolean.TRUE.equals(request.getCanPushOrders())
                && !Boolean.TRUE.equals(partner.getPaymentModeConfirmed())) {
            throw new BadRequestException(
                    "Order push is refused for " + partner.getName() + " until their paymentMode is "
                            + "confirmed: while it is a constant, a cash order arrives marked paid and "
                            + "the venue collects nothing. Menu access is unaffected.");
        }

        grant.setCanReadMenu(Boolean.TRUE.equals(request.getCanReadMenu()));
        grant.setCanPushOrders(Boolean.TRUE.equals(request.getCanPushOrders()));
        grant.setActive(true);

        // FIXED is an absolute price for one dish; as a venue-wide default it would flatten the whole
        // menu to a single number, which is never what anyone means.
        PriceAdjustmentType adjustmentType = request.getPriceAdjustmentType() == null
                ? PriceAdjustmentType.NONE : request.getPriceAdjustmentType();
        if (adjustmentType == PriceAdjustmentType.FIXED) {
            throw new BadRequestException(
                    "A venue-wide markup must be NONE, PERCENT or AMOUNT — FIXED applies to one item");
        }
        if (adjustmentType == PriceAdjustmentType.PERCENT
                && request.getPriceAdjustmentValue() != null
                && request.getPriceAdjustmentValue().compareTo(new BigDecimal("-100")) < 0) {
            throw new BadRequestException("A percentage discount cannot exceed 100%");
        }
        grant.setPriceAdjustmentType(adjustmentType);
        grant.setPriceAdjustmentValue(request.getPriceAdjustmentValue() == null
                ? BigDecimal.ZERO : request.getPriceAdjustmentValue());
        grant.setPriceRounding(request.getPriceRounding() == null
                ? BigDecimal.ZERO : request.getPriceRounding());
        partnerRestaurantRepository.save(grant);

        // Every price this partner holds for the venue just moved. One message, not one per item —
        // a venue-wide markup across four hundred dishes would otherwise be four hundred deliveries
        // for a single click.
        partnerMenuNotifier.venuePricingChanged(partner.getId(), restaurant.getId());

        log.info("Partner {} granted restaurant {} (menu={}, orders={})",
                partner.getSlug(), restaurant.getId(), grant.getCanReadMenu(), grant.getCanPushOrders());
        return toResponse(partner);
    }

    /**
     * Revokes a venue by deactivating the grant rather than deleting the row, so the history of who was
     * once connected survives and restoring access is one flag rather than a re-grant from memory.
     */
    @Transactional
    public PartnerAdminResponse revokeRestaurant(Long partnerId, Long restaurantId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        partnerRestaurantRepository.findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .ifPresent(grant -> {
                    grant.setActive(false);
                    partnerRestaurantRepository.save(grant);
                    log.info("Partner {} revoked from restaurant {}", partner.getSlug(), restaurantId);
                });
        return toResponse(partner);
    }

    /**
     * Create or update one price override. Upserted on (partner, venue, scope, target) so an operator
     * adjusting the same dish twice edits it rather than accumulating contradictory rules.
     */
    @Transactional
    public PartnerAdminResponse upsertPriceRule(Long partnerId, Long restaurantId,
                                                PartnerPriceRuleRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));
        partnerRestaurantRepository.findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .orElseThrow(() -> new BadRequestException(
                        "Grant this partner the venue before pricing items on it"));

        if (request.getAdjustmentType() == PriceAdjustmentType.NONE) {
            // A rule that adjusts nothing is indistinguishable from no rule, and leaving one behind
            // makes the override list lie about what is configured.
            throw new BadRequestException("Delete the rule instead of setting it to NONE");
        }
        if (request.getAdjustmentType() == PriceAdjustmentType.FIXED
                && request.getAdjustmentValue().signum() < 0) {
            throw new BadRequestException("A fixed price cannot be negative");
        }
        if (request.getScope() == PriceRuleScope.CATEGORY
                && request.getAdjustmentType() == PriceAdjustmentType.FIXED) {
            throw new BadRequestException(
                    "A fixed price cannot apply to a whole category — every item in it would cost the same");
        }

        PartnerPriceRule rule = partnerPriceRuleRepository
                .findByPartnerIdAndRestaurantIdAndScopeAndTargetId(
                        partnerId, restaurantId, request.getScope(), request.getTargetId())
                .orElseGet(() -> PartnerPriceRule.builder()
                        .partnerId(partnerId)
                        .restaurantId(restaurantId)
                        .scope(request.getScope())
                        .targetId(request.getTargetId())
                        .build());

        rule.setAdjustmentType(request.getAdjustmentType());
        rule.setAdjustmentValue(request.getAdjustmentValue());
        rule.setActive(true);
        partnerPriceRuleRepository.save(rule);
        partnerMenuNotifier.venuePricingChanged(partnerId, restaurantId);

        log.info("Partner {} price rule at restaurant {}: {} {} {} {}",
                partner.getSlug(), restaurantId, request.getScope(), request.getTargetId(),
                request.getAdjustmentType(), request.getAdjustmentValue());
        return toResponse(partner);
    }

    /** Remove one override. The item falls back to the venue default on the next menu pull. */
    @Transactional
    public PartnerAdminResponse deletePriceRule(Long partnerId, Long ruleId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        partnerPriceRuleRepository.findById(ruleId)
                .filter(rule -> rule.getPartnerId().equals(partnerId))
                .ifPresent(rule -> {
                    Long restaurantId = rule.getRestaurantId();
                    partnerPriceRuleRepository.delete(rule);
                    // Removing an override is a price change too: the item drops back to the venue
                    // default, which is a different number from the one they are holding.
                    partnerMenuNotifier.venuePricingChanged(partnerId, restaurantId);
                    log.info("Deleted price rule {} for partner {}", ruleId, partner.getSlug());
                });

        return toResponse(partner);
    }

    /**
     * The overrides for one venue, with each target's name resolved so the UI can render "Plov +20%"
     * rather than "PRODUCT 412 +20%".
     */
    private List<PartnerAdminResponse.PriceRule> priceRulesFor(Long partnerId, Long restaurantId) {
        return partnerPriceRuleRepository.findByPartnerIdAndRestaurantId(partnerId, restaurantId).stream()
                .map(rule -> PartnerAdminResponse.PriceRule.builder()
                        .id(rule.getId())
                        .scope(rule.getScope())
                        .targetId(rule.getTargetId())
                        .targetName(nameOfTarget(rule))
                        .adjustmentType(rule.getAdjustmentType())
                        .adjustmentValue(rule.getAdjustmentValue())
                        .active(rule.getActive())
                        .build())
                .sorted(java.util.Comparator.comparing(PartnerAdminResponse.PriceRule::getScope)
                        .thenComparing(PartnerAdminResponse.PriceRule::getTargetId))
                .toList();
    }

    /** Null when the target has since been deleted — an orphan rule is inert, so this just shows it. */
    private String nameOfTarget(PartnerPriceRule rule) {
        return switch (rule.getScope()) {
            case CATEGORY -> categoryRepository.findById(rule.getTargetId())
                    .map(com.elcafe.modules.menu.entity.Category::getName).orElse(null);
            case PRODUCT -> productRepository.findById(rule.getTargetId())
                    .map(com.elcafe.modules.menu.entity.Product::getName).orElse(null);
            case VARIANT -> productVariantRepository.findById(rule.getTargetId())
                    .map(com.elcafe.modules.menu.entity.ProductVariant::getName).orElse(null);
        };
    }

    /**
     * Put a partner's dead-lettered messages back in the queue — the "they are back up" button.
     *
     * <p>Attempts reset, because carrying the old count over would dead-letter them again on the first
     * hiccup after a recovery.
     */
    @Transactional
    public PartnerAdminResponse retryDeadLetters(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", "id", partnerId));

        List<IntegrationEvent> dead = integrationEventRepository
                .findByPartnerIdAndStatusOrderByCreatedAtDesc(
                        partnerId, IntegrationEventStatus.DEAD_LETTER, PageRequest.of(0, 500));
        dead.forEach(IntegrationEvent::requeue);
        integrationEventRepository.saveAll(dead);

        log.info("Requeued {} dead-lettered events for partner {}", dead.size(), partner.getSlug());
        return toResponse(partner);
    }

    private PartnerAdminResponse toResponse(Partner partner) {
        List<PartnerRestaurant> rows = partnerRestaurantRepository.findByPartnerId(partner.getId());

        // Name only the venues this partner actually touches. Loading every restaurant to label a
        // handful of grants would scale with the size of the platform rather than with the answer.
        Map<Long, String> restaurantNames = restaurantRepository.findAllById(
                        rows.stream().map(PartnerRestaurant::getRestaurantId).distinct().toList()).stream()
                .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName, (a, b) -> a));

        List<PartnerAdminResponse.Grant> grants = rows.stream()
                .map(grant -> PartnerAdminResponse.Grant.builder()
                        .restaurantId(grant.getRestaurantId())
                        .restaurantName(restaurantNames.get(grant.getRestaurantId()))
                        .canReadMenu(grant.getCanReadMenu())
                        .canPushOrders(grant.getCanPushOrders())
                        .active(grant.getActive())
                        .priceAdjustmentType(grant.getPriceAdjustmentType())
                        .priceAdjustmentValue(grant.getPriceAdjustmentValue())
                        .priceRounding(grant.getPriceRounding())
                        .priceRules(priceRulesFor(partner.getId(), grant.getRestaurantId()))
                        .build())
                .sorted(java.util.Comparator.comparing(
                        PartnerAdminResponse.Grant::getRestaurantId,
                        java.util.Comparator.nullsLast(Long::compareTo)))
                .toList();

        return PartnerAdminResponse.builder()
                .id(partner.getId())
                .name(partner.getName())
                .slug(partner.getSlug())
                .apiKeyPrefix(partner.getApiKeyPrefix())
                .contactEmail(partner.getContactEmail())
                .active(partner.getActive())
                .customerFeePercent(partner.getCustomerFeePercent())
                .paymentModeConfirmed(partner.getPaymentModeConfirmed())
                .createdAt(partner.getCreatedAt())
                .restaurants(grants)
                .pendingEvents(integrationEventRepository.countByPartnerIdAndStatus(
                        partner.getId(), IntegrationEventStatus.PENDING))
                .deadLetteredEvents(integrationEventRepository.countByPartnerIdAndStatus(
                        partner.getId(), IntegrationEventStatus.DEAD_LETTER))
                .build();
    }
}
