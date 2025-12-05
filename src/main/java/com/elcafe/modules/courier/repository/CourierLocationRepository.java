package com.elcafe.modules.courier.repository;

import com.elcafe.modules.courier.entity.CourierLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CourierLocationRepository extends JpaRepository<CourierLocation, Long> {

    /**
     * Get latest location for a courier
     */
    Optional<CourierLocation> findFirstByCourierIdOrderByTimestampDesc(Long courierId);

    /**
     * Get latest location for a specific order delivery
     */
    Optional<CourierLocation> findFirstByOrderIdOrderByTimestampDesc(Long orderId);

    /**
     * Get location history for a courier
     */
    List<CourierLocation> findByCourierIdOrderByTimestampDesc(Long courierId);

    /**
     * Get location history for a courier within time range
     */
    @Query("SELECT cl FROM CourierLocation cl WHERE cl.courier.id = :courierId " +
            "AND cl.timestamp BETWEEN :startTime AND :endTime ORDER BY cl.timestamp DESC")
    List<CourierLocation> findByCourierIdAndTimestampBetween(
            @Param("courierId") Long courierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Get all location updates for an order
     */
    List<CourierLocation> findByOrderIdOrderByTimestampAsc(Long orderId);

    /**
     * Get active courier locations (last update within specified minutes)
     */
    @Query("SELECT cl FROM CourierLocation cl WHERE cl.isActive = true " +
            "AND cl.timestamp > :cutoffTime " +
            "AND cl.id IN (SELECT MAX(cl2.id) FROM CourierLocation cl2 GROUP BY cl2.courier.id)")
    List<CourierLocation> findActiveCourierLocations(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Delete old location data (for cleanup/privacy)
     */
    void deleteByTimestampBefore(LocalDateTime cutoffDate);
}
