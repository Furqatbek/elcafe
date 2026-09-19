package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.entity.PartnerPriceRule;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerPriceRuleRepository extends JpaRepository<PartnerPriceRule, Long> {

    /**
     * Every active rule for one partner at one venue, in a single read. The resolver loads this once
     * per menu build or per order rather than querying per item — a menu with 300 products would
     * otherwise be 300 round trips to discover that almost none of them have an override.
     */
    List<PartnerPriceRule> findByPartnerIdAndRestaurantIdAndActiveTrue(Long partnerId, Long restaurantId);

    List<PartnerPriceRule> findByPartnerIdAndRestaurantId(Long partnerId, Long restaurantId);

    Optional<PartnerPriceRule> findByPartnerIdAndRestaurantIdAndScopeAndTargetId(
            Long partnerId, Long restaurantId, PriceRuleScope scope, Long targetId);
}
