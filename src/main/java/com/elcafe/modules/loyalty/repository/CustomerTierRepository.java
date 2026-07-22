package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.CustomerTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface CustomerTierRepository extends JpaRepository<CustomerTier, Long> {

    Optional<CustomerTier> findByName(String name);

    Optional<CustomerTier> findByLevel(Integer level);

    @Query("SELECT t FROM CustomerTier t WHERE " +
           "t.minTotalSpend <= :totalSpent AND t.minOrderCount <= :orderCount " +
           "ORDER BY t.level DESC LIMIT 1")
    Optional<CustomerTier> findHighestQualifiedTier(
        @Param("totalSpent") BigDecimal totalSpent,
        @Param("orderCount") Integer orderCount
    );
}
