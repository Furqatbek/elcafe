package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Mirrors {@code TelegramTemplateRepository}, adapted to Instagram's per-tenant model: every finder is
 * explicitly tenant-scoped, since a template belongs to exactly one restaurant's own library.
 */
@Repository
public interface InstagramTemplateRepository extends JpaRepository<InstagramTemplate, Long> {

    /** Tenant-scoped by-id lookup — closes the IDOR a bare {@code findById} leaves open. */
    Optional<InstagramTemplate> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** One tenant's templates, newest first. */
    Page<InstagramTemplate> findByRestaurantIdOrderByIdDesc(Long restaurantId, Pageable pageable);

    /** Tenant-scoped uniqueness check backing the UNIQUE(restaurant_id, name) constraint. */
    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
