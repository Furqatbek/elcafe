package com.elcafe.modules.pos.offline.repository;

import com.elcafe.modules.pos.offline.entity.POSDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface POSDeviceRepository extends JpaRepository<POSDevice, Long> {

    Optional<POSDevice> findByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);

    List<POSDevice> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    @Query("SELECT d FROM POSDevice d WHERE d.restaurant.id = :restaurantId AND d.lastHeartbeat > :cutoff")
    List<POSDevice> findOnlineDevices(@Param("restaurantId") Long restaurantId,
                                      @Param("cutoff") OffsetDateTime cutoff);

    @Query("SELECT d FROM POSDevice d WHERE d.restaurant.id = :restaurantId AND " +
           "(d.lastHeartbeat IS NULL OR d.lastHeartbeat < :cutoff)")
    List<POSDevice> findOfflineDevices(@Param("restaurantId") Long restaurantId,
                                       @Param("cutoff") OffsetDateTime cutoff);

    boolean existsByRestaurantIdAndDeviceId(Long restaurantId, String deviceId);
}
