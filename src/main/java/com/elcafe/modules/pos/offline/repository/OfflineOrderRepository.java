package com.elcafe.modules.pos.offline.repository;

import com.elcafe.modules.pos.offline.entity.OfflineOrder;
import com.elcafe.modules.pos.offline.enums.OfflineSyncStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfflineOrderRepository extends JpaRepository<OfflineOrder, Long> {

    Optional<OfflineOrder> findByRestaurantIdAndDeviceIdAndClientOrderId(
        Long restaurantId, String deviceId, String clientOrderId);

    List<OfflineOrder> findByRestaurantIdAndSyncStatus(Long restaurantId, OfflineSyncStatus status);

    List<OfflineOrder> findByRestaurantIdAndDeviceIdAndSyncStatus(
        Long restaurantId, String deviceId, OfflineSyncStatus status);

    @Query("SELECT o FROM OfflineOrder o WHERE o.syncStatus = :status AND o.syncAttempts < :maxAttempts " +
           "ORDER BY o.createdAt ASC")
    List<OfflineOrder> findPendingForSync(@Param("status") OfflineSyncStatus status,
                                          @Param("maxAttempts") int maxAttempts);

    Page<OfflineOrder> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM OfflineOrder o WHERE o.restaurant.id = :restaurantId AND o.syncStatus = :status")
    long countByRestaurantIdAndStatus(@Param("restaurantId") Long restaurantId,
                                      @Param("status") OfflineSyncStatus status);

    @Query("SELECT o FROM OfflineOrder o WHERE o.syncStatus = 'FAILED' AND o.lastSyncAttempt < :cutoff")
    List<OfflineOrder> findFailedOrdersForRetry(@Param("cutoff") OffsetDateTime cutoff);
}
