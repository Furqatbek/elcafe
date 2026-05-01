package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.PackagingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PackagingRuleRepository extends JpaRepository<PackagingRule, Long> {

    List<PackagingRule> findByProductIdAndActiveTrue(Long productId);

    List<PackagingRule> findByRestaurantIdAndActiveTrue(Long restaurantId);

    List<PackagingRule> findByRestaurantId(Long restaurantId);

    List<PackagingRule> findByProductId(Long productId);
}
