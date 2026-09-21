package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.MenuCollectionItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for MenuCollectionItem entity
 */
@Repository
public interface MenuCollectionItemRepository extends JpaRepository<MenuCollectionItem, Long> {

    List<MenuCollectionItem> findByMenuCollectionId(Long menuCollectionId);

    List<MenuCollectionItem> findByProductId(Long productId);

    @Query("SELECT mci FROM MenuCollectionItem mci WHERE mci.menuCollection.id = :menuCollectionId " +
           "ORDER BY mci.sortOrder ASC, mci.id ASC")
    List<MenuCollectionItem> findByMenuCollectionIdOrderBySortOrder(@Param("menuCollectionId") Long menuCollectionId);

    void deleteByMenuCollectionIdAndProductId(Long menuCollectionId, Long productId);

    boolean existsByMenuCollectionIdAndProductId(Long menuCollectionId, Long productId);
}
