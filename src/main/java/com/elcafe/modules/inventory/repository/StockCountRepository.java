package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.StockCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    List<StockCount> findByRestaurantId(Long restaurantId);

    Page<StockCount> findByRestaurantId(Long restaurantId, Pageable pageable);

    Optional<StockCount> findByCountNumber(String countNumber);

    List<StockCount> findByRestaurantIdAndStatus(Long restaurantId, StockCount.Status status);

    List<StockCount> findByRestaurantIdAndCountType(Long restaurantId, StockCount.CountType countType);

    @Query("SELECT sc FROM StockCount sc WHERE sc.restaurant.id = :restaurantId " +
           "AND sc.scheduledDate BETWEEN :startDate AND :endDate " +
           "ORDER BY sc.scheduledDate ASC")
    List<StockCount> findByRestaurantIdAndScheduledDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT sc FROM StockCount sc WHERE sc.restaurant.id = :restaurantId " +
           "AND sc.status IN ('DRAFT', 'IN_PROGRESS', 'PENDING_REVIEW') " +
           "ORDER BY sc.createdAt DESC")
    List<StockCount> findActiveStockCounts(Long restaurantId);

    @Query("SELECT sc FROM StockCount sc WHERE sc.restaurant.id = :restaurantId " +
           "AND sc.status = 'APPROVED' " +
           "ORDER BY sc.completedAt DESC")
    List<StockCount> findCompletedStockCounts(Long restaurantId);

    @Query("SELECT COUNT(sc) FROM StockCount sc WHERE sc.restaurant.id = :restaurantId " +
           "AND sc.countNumber LIKE :prefix%")
    long countByRestaurantIdAndCountNumberPrefix(Long restaurantId, String prefix);
}
