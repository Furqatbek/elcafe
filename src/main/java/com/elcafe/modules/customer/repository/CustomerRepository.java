package com.elcafe.modules.customer.repository;

import com.elcafe.modules.customer.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByQrCode(String qrCode);

    List<Customer> findByPhoneContaining(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    // --- Per-restaurant finders (V150: customers are tenant-scoped). Used by flows that resolve a
    // restaurant explicitly (consumer login, order/reservation/self-service customer creation) so a
    // phone/email shared across restaurants maps to the right per-restaurant row. ---

    Optional<Customer> findByPhoneAndRestaurantId(String phone, Long restaurantId);

    Optional<Customer> findByEmailAndRestaurantId(String email, Long restaurantId);

    boolean existsByPhoneAndRestaurantId(String phone, Long restaurantId);

    boolean existsByEmailAndRestaurantId(String email, Long restaurantId);

    List<Customer> findByPhoneContainingAndRestaurantId(String phone, Long restaurantId);

    /**
     * Resolve a customer's PRIMARY record (the oldest row for a phone) for GLOBAL channels that
     * carry no restaurant context — the Telegram/Instagram bots, whose subscriber rows are not
     * tenant-scoped. Per-restaurant flows must use {@link #findByPhoneAndRestaurantId} instead.
     */
    Optional<Customer> findFirstByPhoneOrderByIdAsc(String phone);

    Page<Customer> findByRestaurantId(Long restaurantId, org.springframework.data.domain.Pageable pageable);

    List<Customer> findByActiveTrue();

    /** Tenant-scoped variant of {@link #findByActiveTrue()} (§3.3 — admin activity/RFM listing). */
    List<Customer> findByRestaurantIdAndActiveTrue(Long restaurantId);

    @Query("SELECT c FROM Customer c WHERE MONTH(c.birthDate) = :month AND DAY(c.birthDate) = :day")
    List<Customer> findByBirthDateMonthAndDay(@Param("month") int month, @Param("day") int day);

    @Query("SELECT c FROM Customer c WHERE c.active = true AND c.id NOT IN " +
           "(SELECT DISTINCT o.customer.id FROM Order o WHERE o.createdAt >= :since AND o.customer IS NOT NULL)")
    List<Customer> findInactiveCustomers(@Param("since") OffsetDateTime since);

    List<Customer> findByCreatedAtAfter(OffsetDateTime since);

    /**
     * Find customers created before a specific date (for retention analysis)
     */
    @Query("SELECT c FROM Customer c WHERE c.createdAt < :before")
    List<Customer> findByCreatedAtBefore(@Param("before") OffsetDateTime before);

    /**
     * Find customers created within a date range (for retention analysis)
     */
    @Query("SELECT c FROM Customer c WHERE c.createdAt >= :start AND c.createdAt <= :end")
    List<Customer> findByCreatedAtBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /**
     * Find customers created up to a specific date (for retention analysis)
     */
    @Query("SELECT c FROM Customer c WHERE c.createdAt <= :before")
    List<Customer> findByCreatedAtLessThanEqual(@Param("before") OffsetDateTime before);

    /**
     * Count customers created before a specific date
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt < :before")
    long countByCreatedAtBefore(@Param("before") OffsetDateTime before);

    /**
     * Count customers created within a date range
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt >= :start AND c.createdAt <= :end")
    long countByCreatedAtBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /**
     * Count customers created up to a specific date
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt <= :before")
    long countByCreatedAtLessThanEqual(@Param("before") OffsetDateTime before);
}
