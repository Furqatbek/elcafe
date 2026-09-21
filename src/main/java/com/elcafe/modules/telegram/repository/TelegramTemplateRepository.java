package com.elcafe.modules.telegram.repository;

import com.elcafe.modules.telegram.entity.TelegramTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramTemplateRepository extends JpaRepository<TelegramTemplate, Long> {

    List<TelegramTemplate> findByIsActiveTrue();

    List<TelegramTemplate> findByTypeAndIsActiveTrue(String type);

    boolean existsByName(String name);

    @Query("SELECT DISTINCT t.type FROM TelegramTemplate t ORDER BY t.type")
    List<String> findAllTypes();

    @Query("SELECT t FROM TelegramTemplate t WHERE " +
           "(LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.content) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<TelegramTemplate> searchTemplates(@Param("search") String search, Pageable pageable);
}
