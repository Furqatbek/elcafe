package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.entity.Partner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerRepository extends JpaRepository<Partner, Long> {

    /**
     * The authentication lookup. Hashing the presented key and hitting the unique index means one
     * indexed read per request, and means no query ever carries the raw key.
     */
    Optional<Partner> findByApiKeyHash(String apiKeyHash);

    Optional<Partner> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
