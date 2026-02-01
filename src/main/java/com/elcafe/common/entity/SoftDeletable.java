package com.elcafe.common.entity;

import java.time.OffsetDateTime;

/**
 * Interface for entities that support soft deletion.
 * Entities implementing this interface will not be physically deleted,
 * but marked with a deletedAt timestamp instead.
 */
public interface SoftDeletable {

    OffsetDateTime getDeletedAt();

    void setDeletedAt(OffsetDateTime deletedAt);

    String getDeletedBy();

    void setDeletedBy(String deletedBy);

    default boolean isDeleted() {
        return getDeletedAt() != null;
    }

    default void softDelete(String deletedBy) {
        setDeletedAt(OffsetDateTime.now());
        setDeletedBy(deletedBy);
    }

    default void restore() {
        setDeletedAt(null);
        setDeletedBy(null);
    }
}
