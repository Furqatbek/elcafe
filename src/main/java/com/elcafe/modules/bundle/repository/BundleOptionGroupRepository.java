package com.elcafe.modules.bundle.repository;

import com.elcafe.modules.bundle.entity.BundleOptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for BundleOptionGroup entities.
 * Note: Option groups are managed via cascade from Bundle entity,
 * so direct find/delete operations are not needed.
 */
@Repository
public interface BundleOptionGroupRepository extends JpaRepository<BundleOptionGroup, Long> {
}
