package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByRestaurantIdOrderByNameAsc(Long restaurantId);

    List<Supplier> findByRestaurantIdAndActiveTrueOrderByNameAsc(Long restaurantId);

    Optional<Supplier> findByRestaurantIdAndCode(Long restaurantId, String code);

    boolean existsByRestaurantIdAndCode(Long restaurantId, String code);

    boolean existsByRestaurantIdAndCodeAndIdNot(Long restaurantId, String code, Long id);

    @Query("SELECT s FROM Supplier s WHERE s.restaurant.id = :restaurantId AND s.active = true ORDER BY s.rating DESC")
    List<Supplier> findTopPerformers(Long restaurantId);

    long countByRestaurantIdAndActiveTrue(Long restaurantId);
}
