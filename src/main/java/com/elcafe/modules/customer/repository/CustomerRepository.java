package com.elcafe.modules.customer.repository;

import com.elcafe.modules.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    List<Customer> findByActiveTrue();

    List<Customer> findByCreatedAtAfter(LocalDateTime since);

    @Query("SELECT c FROM Customer c WHERE EXTRACT(MONTH FROM c.birthDate) = :month AND EXTRACT(DAY FROM c.birthDate) = :day AND c.active = true")
    List<Customer> findByBirthDateMonthAndDay(@Param("month") int month, @Param("day") int day);

    @Query("SELECT c FROM Customer c WHERE c.active = true AND c.id NOT IN " +
           "(SELECT DISTINCT o.customer.id FROM Order o WHERE o.createdAt >= :since)")
    List<Customer> findInactiveCustomers(@Param("since") LocalDateTime since);

    @Query("SELECT c FROM Customer c WHERE c.active = true AND c.phone IS NOT NULL AND c.phone != ''")
    List<Customer> findCustomersWithPhone();

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.active = true")
    long countActiveCustomers();
}
