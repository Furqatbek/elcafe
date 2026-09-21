package com.elcafe.modules.push.repository;

import com.elcafe.modules.push.entity.PushTemplate;
import com.elcafe.modules.push.enums.PushNotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushTemplateRepository extends JpaRepository<PushTemplate, Long> {

    /**
     * Find active templates
     */
    Page<PushTemplate> findByIsActiveTrueOrderByNameAsc(Pageable pageable);

    /**
     * Find all active templates
     */
    List<PushTemplate> findByIsActiveTrueOrderByNameAsc();

    /**
     * Find templates by type
     */
    List<PushTemplate> findByTypeAndIsActiveTrueOrderByNameAsc(PushNotificationType type);

    /**
     * Find template by name
     */
    Optional<PushTemplate> findByNameAndIsActiveTrue(String name);

    /**
     * Search templates by name
     */
    @Query("SELECT t FROM PushTemplate t WHERE t.isActive = true AND LOWER(t.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<PushTemplate> searchByName(@Param("query") String query);

    /**
     * Count active templates
     */
    long countByIsActiveTrue();

    /**
     * Count templates by type
     */
    long countByTypeAndIsActiveTrue(PushNotificationType type);

    /**
     * Increment usage count
     */
    @Modifying
    @Query("UPDATE PushTemplate t SET t.usageCount = t.usageCount + 1 WHERE t.id = :templateId")
    void incrementUsageCount(@Param("templateId") Long templateId);

    /**
     * Check if name exists
     */
    boolean existsByNameAndIsActiveTrue(String name);

    /**
     * Find most used templates
     */
    @Query("SELECT t FROM PushTemplate t WHERE t.isActive = true ORDER BY t.usageCount DESC")
    List<PushTemplate> findMostUsed(Pageable pageable);
}
