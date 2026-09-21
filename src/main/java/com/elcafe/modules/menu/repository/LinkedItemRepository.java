package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.LinkedItem;
import com.elcafe.modules.menu.enums.LinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LinkedItem entity
 */
@Repository
public interface LinkedItemRepository extends JpaRepository<LinkedItem, Long> {

    List<LinkedItem> findByProductId(Long productId);

    List<LinkedItem> findByProductIdAndLinkType(Long productId, LinkType linkType);

    @Query("SELECT li FROM LinkedItem li WHERE li.product.id = :productId " +
           "ORDER BY li.sortOrder ASC, li.id ASC")
    List<LinkedItem> findByProductIdOrderBySortOrder(@Param("productId") Long productId);

    @Query("SELECT li FROM LinkedItem li WHERE li.product.id = :productId AND li.linkType = :linkType " +
           "ORDER BY li.sortOrder ASC, li.id ASC")
    List<LinkedItem> findByProductIdAndLinkTypeOrderBySortOrder(
            @Param("productId") Long productId,
            @Param("linkType") LinkType linkType
    );

    void deleteByProductIdAndLinkedProductId(Long productId, Long linkedProductId);

    boolean existsByProductIdAndLinkedProductId(Long productId, Long linkedProductId);

    /**
     * Batch fetch linked items by IDs with their linked products eagerly loaded.
     * Use this to avoid N+1 queries when loading multiple modifiers.
     */
    @Query("SELECT li FROM LinkedItem li LEFT JOIN FETCH li.linkedProduct WHERE li.id IN :ids")
    List<LinkedItem> findAllByIdWithLinkedProduct(@Param("ids") List<Long> ids);
}
