package com.elcafe.modules.bundle.repository;

import com.elcafe.modules.bundle.entity.BundleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for BundleItem entities.
 * Note: Bundle items are managed via cascade from Bundle entity,
 * so direct find/delete operations are not needed.
 */
@Repository
public interface BundleItemRepository extends JpaRepository<BundleItem, Long> {
}
