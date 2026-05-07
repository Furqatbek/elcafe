package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {

    Optional<EmployeeShift> findByEmployeeIdAndStatus(Long employeeId, ShiftStatus status);

    List<EmployeeShift> findByRestaurantIdAndShiftDate(Long restaurantId, LocalDate date);

    List<EmployeeShift> findByRestaurantIdAndShiftDateAndStatus(Long restaurantId, LocalDate date, ShiftStatus status);

    Page<EmployeeShift> findByEmployeeIdOrderByShiftDateDesc(Long employeeId, Pageable pageable);

    @Query("SELECT s FROM EmployeeShift s WHERE s.employee.id = :employeeId AND s.status = 'ACTIVE'")
    Optional<EmployeeShift> findActiveShiftByEmployee(@Param("employeeId") Long employeeId);

    @Query("SELECT s FROM EmployeeShift s WHERE s.waiter.id = :waiterId AND s.status = 'ACTIVE'")
    Optional<EmployeeShift> findActiveShiftByWaiter(@Param("waiterId") Long waiterId);

    @Query("SELECT s FROM EmployeeShift s WHERE s.restaurant.id = :restaurantId AND s.status IN ('ACTIVE', 'ON_BREAK')")
    List<EmployeeShift> findActiveShiftsByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT s FROM EmployeeShift s WHERE s.restaurant.id = :restaurantId " +
           "AND s.shiftDate BETWEEN :startDate AND :endDate ORDER BY s.shiftDate, s.clockIn")
    List<EmployeeShift> findByRestaurantAndDateRange(@Param("restaurantId") Long restaurantId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    @Query("SELECT s FROM EmployeeShift s WHERE s.restaurant.id = :restaurantId AND s.status = 'COMPLETED' " +
           "AND s.approvedBy IS NULL ORDER BY s.clockOut")
    List<EmployeeShift> findPendingApproval(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(s) FROM EmployeeShift s WHERE s.restaurant.id = :restaurantId " +
           "AND s.shiftDate = :date AND s.status = 'ACTIVE'")
    long countActiveShifts(@Param("restaurantId") Long restaurantId, @Param("date") LocalDate date);

    boolean existsByEmployeeIdAndStatusAndShiftDate(Long employeeId, ShiftStatus status, LocalDate date);
}
