package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.SalaryConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SalaryConfigRepository extends JpaRepository<SalaryConfig, Long> {

    List<SalaryConfig> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    List<SalaryConfig> findByRestaurant_Id(Long restaurantId);

    Optional<SalaryConfig> findByRestaurant_IdAndEmployee_Id(Long restaurantId, Long employeeId);

    @Query("SELECT sc FROM SalaryConfig sc WHERE sc.active = true AND sc.payDay = :dayOfMonth " +
           "AND (sc.lastPaidDate IS NULL OR sc.lastPaidDate < :today)")
    List<SalaryConfig> findDueForPayment(int dayOfMonth, LocalDate today);
}
