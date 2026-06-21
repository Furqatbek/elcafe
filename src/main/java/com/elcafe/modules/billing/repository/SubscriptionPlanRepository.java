package com.elcafe.modules.billing.repository;

import com.elcafe.modules.billing.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository over the {@link SubscriptionPlan} catalogue. Platform-level (not tenant-scoped); the
 * inherited {@code TenantScopedJpaRepository.findById} simply issues a query-by-id with no filter,
 * since {@code subscription_plan} carries no {@code restaurant_id}.
 */
@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {

    Optional<SubscriptionPlan> findByCode(String code);

    List<SubscriptionPlan> findAllByActiveTrueOrderBySortOrderAsc();
}
