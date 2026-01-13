package com.elcafe.modules.selfservice.repository;

import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.enums.QRCodeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QRCodeRepository extends JpaRepository<QRCode, Long> {

    Optional<QRCode> findByCode(String code);

    boolean existsByCode(String code);

    Page<QRCode> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    List<QRCode> findByRestaurantIdAndIsActiveTrue(Long restaurantId);

    List<QRCode> findByRestaurantIdAndQrTypeAndIsActiveTrue(Long restaurantId, QRCodeType qrType);

    Optional<QRCode> findByTableId(Long tableId);

    @Query("SELECT q FROM QRCode q WHERE q.restaurant.id = :restaurantId AND q.table IS NULL AND q.isActive = true")
    List<QRCode> findUnassignedByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(q) FROM QRCode q WHERE q.restaurant.id = :restaurantId AND q.isActive = true")
    long countActiveByRestaurant(@Param("restaurantId") Long restaurantId);

    @Query("SELECT SUM(q.scanCount) FROM QRCode q WHERE q.restaurant.id = :restaurantId")
    Long getTotalScans(@Param("restaurantId") Long restaurantId);
}
