package com.elcafe.modules.pos.tax.repository;

import com.elcafe.modules.pos.tax.entity.TaxExemptionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TaxExemptionLogRepository extends JpaRepository<TaxExemptionLog, Long> {

    List<TaxExemptionLog> findByOrderId(Long orderId);

    List<TaxExemptionLog> findByCustomerId(Long customerId);

    Page<TaxExemptionLog> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    @Query("SELECT SUM(l.taxAmountExempted) FROM TaxExemptionLog l " +
           "WHERE l.restaurant.id = :restaurantId AND l.createdAt BETWEEN :start AND :end")
    BigDecimal sumExemptedAmountByRestaurantAndDateRange(@Param("restaurantId") Long restaurantId,
                                                         @Param("start") OffsetDateTime start,
                                                         @Param("end") OffsetDateTime end);
}
