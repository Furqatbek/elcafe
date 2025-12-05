package com.elcafe.modules.courier.repository;

import com.elcafe.modules.courier.entity.CourierAttendance;
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
public interface CourierAttendanceRepository extends JpaRepository<CourierAttendance, Long> {

    /**
     * Find attendance records for a courier with pagination
     */
    Page<CourierAttendance> findByCourierProfileIdOrderByDateDesc(Long courierProfileId, Pageable pageable);

    /**
     * Find attendance record for a courier on a specific date
     */
    Optional<CourierAttendance> findByCourierProfileIdAndDate(Long courierProfileId, LocalDate date);

    /**
     * Find attendance records for a courier within date range
     */
    @Query("SELECT a FROM CourierAttendance a WHERE a.courierProfile.id = :courierProfileId " +
            "AND a.date BETWEEN :startDate AND :endDate ORDER BY a.date DESC")
    List<CourierAttendance> findByCourierProfileIdAndDateRange(
            @Param("courierProfileId") Long courierProfileId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Find attendance records where courier was present
     */
    Page<CourierAttendance> findByCourierProfileIdAndPresentTrueOrderByDateDesc(Long courierProfileId, Pageable pageable);

    /**
     * Count attendance days for a courier within date range
     */
    @Query("SELECT COUNT(a) FROM CourierAttendance a WHERE a.courierProfile.id = :courierProfileId " +
            "AND a.present = true AND a.date BETWEEN :startDate AND :endDate")
    long countAttendanceDays(
            @Param("courierProfileId") Long courierProfileId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * Check if attendance record exists for a courier on a specific date
     */
    boolean existsByCourierProfileIdAndDate(Long courierProfileId, LocalDate date);

    /**
     * Delete all attendance records for a courier
     */
    void deleteByCourierProfileId(Long courierProfileId);
}
