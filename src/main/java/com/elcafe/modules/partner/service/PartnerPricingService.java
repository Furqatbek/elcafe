package com.elcafe.modules.partner.service;

import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.repository.PartnerPriceRuleRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a {@link PartnerPriceResolver} for one partner at one venue. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerPricingService {

    private final PartnerRestaurantRepository partnerRestaurantRepository;
    private final PartnerPriceRuleRepository partnerPriceRuleRepository;

    /**
     * Two queries, regardless of menu size: the grant carrying the venue default, and every override
     * for that pairing.
     *
     * <p>Falls back to a pass-through resolver when the grant is missing rather than throwing. Callers
     * have already checked authorization by the time they price anything — failing here would turn a
     * pricing question into a second, differently-shaped access error, and the safe answer to "what
     * should this cost?" with no configuration is the base price.
     */
    @Transactional(readOnly = true)
    public PartnerPriceResolver resolverFor(Long partnerId, Long restaurantId) {
        PartnerRestaurant grant = partnerRestaurantRepository
                .findByPartnerIdAndRestaurantId(partnerId, restaurantId)
                .orElse(null);
        if (grant == null) {
            log.warn("Pricing partner {} at restaurant {} with no grant — falling back to base prices",
                    partnerId, restaurantId);
            return PartnerPriceResolver.passThrough();
        }
        return PartnerPriceResolver.of(grant,
                partnerPriceRuleRepository.findByPartnerIdAndRestaurantIdAndActiveTrue(partnerId, restaurantId));
    }
}
