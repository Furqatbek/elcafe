package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.PromotionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionRuleRepository extends JpaRepository<PromotionRule, Long> {

    Optional<PromotionRule> findByPromotion_Id(Long promotionId);

    void deleteByPromotion_Id(Long promotionId);
}
