package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.MenuCollection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for MenuCollection entity
 */
@Repository
public interface MenuCollectionRepository extends JpaRepository<MenuCollection, Long> {

    Page<MenuCollection> findByRestaurantId(Long restaurantId, Pageable pageable);

    Page<MenuCollection> findByRestaurantIdAndIsActive(Long restaurantId, Boolean isActive, Pageable pageable);

    @Query("SELECT mc FROM MenuCollection mc WHERE mc.restaurant.id = :restaurantId AND " +
           "mc.isActive = true AND " +
           "(mc.startDate IS NULL OR mc.startDate <= :date) AND " +
           "(mc.endDate IS NULL OR mc.endDate >= :date)")
    List<MenuCollection> findActiveMenuCollections(
            @Param("restaurantId") Long restaurantId,
            @Param("date") LocalDate date
    );

    @Query("SELECT mc FROM MenuCollection mc WHERE mc.restaurant.id = :restaurantId AND " +
           "(LOWER(mc.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(mc.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<MenuCollection> searchMenuCollections(
            @Param("restaurantId") Long restaurantId,
            @Param("search") String search,
            Pageable pageable
    );
}
