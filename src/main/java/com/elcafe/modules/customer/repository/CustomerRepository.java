package com.elcafe.modules.customer.repository;

import com.elcafe.modules.customer.entity.Customer;
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
}
