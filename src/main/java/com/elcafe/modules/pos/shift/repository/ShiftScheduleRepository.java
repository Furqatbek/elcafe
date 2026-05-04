package com.elcafe.modules.pos.shift.repository;

import com.elcafe.modules.pos.shift.entity.ShiftSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftScheduleRepository extends JpaRepository<ShiftSchedule, Long> {

    List<ShiftSchedule> findByRestaurantIdAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    List<ShiftSchedule> findByEmployeeIdAndShiftDate(Long employeeId, LocalDate date);

    List<ShiftSchedule> findByRestaurantIdAndShiftDate(Long restaurantId, LocalDate date);

    @Query("SELECT s FROM ShiftSchedule s WHERE s.employee.id = :employeeId " +
           "AND s.shiftDate = :date AND s.status != 'CANCELLED'")
    List<ShiftSchedule> findActiveByEmployeeAndDate(
            @Param("employeeId") Long employeeId, @Param("date") LocalDate date);
}
