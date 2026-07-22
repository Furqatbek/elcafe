package com.elcafe.modules.customer.repository;

import com.elcafe.modules.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);

    List<Customer> findByPhoneContaining(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<Customer> findByActiveTrue();

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
    List<Customer> findByCreatedAtBefore(@Param("before") LocalDateTime before);

    /**
     * Find customers created within a date range (for retention analysis)
     */
    @Query("SELECT c FROM Customer c WHERE c.createdAt >= :start AND c.createdAt <= :end")
    List<Customer> findByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Find customers created up to a specific date (for retention analysis)
     */
    @Query("SELECT c FROM Customer c WHERE c.createdAt <= :before")
    List<Customer> findByCreatedAtLessThanEqual(@Param("before") LocalDateTime before);

    /**
     * Count customers created before a specific date
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt < :before")
    long countByCreatedAtBefore(@Param("before") LocalDateTime before);

    /**
     * Count customers created within a date range
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt >= :start AND c.createdAt <= :end")
    long countByCreatedAtBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * Count customers created up to a specific date
     */
    @Query("SELECT COUNT(c) FROM Customer c WHERE c.createdAt <= :before")
    long countByCreatedAtLessThanEqual(@Param("before") LocalDateTime before);
}
