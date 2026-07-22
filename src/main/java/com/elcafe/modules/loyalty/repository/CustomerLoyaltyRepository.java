package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerLoyaltyRepository extends JpaRepository<CustomerLoyalty, Long> {

    Optional<CustomerLoyalty> findByCustomerId(Long customerId);

    @Query("SELECT cl FROM CustomerLoyalty cl WHERE " +
           "cl.lastOrderDate IS NOT NULL AND cl.lastOrderDate < :thresholdDate")
    List<CustomerLoyalty> findInactiveCustomers(@Param("thresholdDate") OffsetDateTime thresholdDate);

    // Note: Disabled until Customer entity has birthdate field
    // Birthday bonuses can be granted manually via API endpoint
    // @Query("SELECT cl FROM CustomerLoyalty cl WHERE " +
    //        "EXTRACT(MONTH FROM cl.customer.birthdate) = :month AND " +
    //        "EXTRACT(DAY FROM cl.customer.birthdate) = :day AND " +
    //        "(cl.birthdayBonusClaimedYear IS NULL OR cl.birthdayBonusClaimedYear < :currentYear)")
    // List<CustomerLoyalty> findCustomersWithBirthdayToday(
    //     @Param("month") int month,
    //     @Param("day") int day,
    //     @Param("currentYear") int currentYear
    // );

    @Query("SELECT COUNT(cl) FROM CustomerLoyalty cl WHERE cl.tier.id = :tierId")
    Long countByTierId(@Param("tierId") Long tierId);

    /**
     * Find loyalty records for a list of customer IDs (for bulk operations)
     */
    @Query("SELECT cl FROM CustomerLoyalty cl WHERE cl.customer.id IN :customerIds")
    List<CustomerLoyalty> findByCustomerIdIn(@Param("customerIds") java.util.Collection<Long> customerIds);
}
