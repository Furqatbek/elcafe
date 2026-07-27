package com.elcafe.modules.customer.repository;

import com.elcafe.modules.customer.entity.CustomerPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerPreferenceRepository extends JpaRepository<CustomerPreference, Long> {

    /**
     * Every preference for one guest, grouped by kind so the profile can render allergies first without
     * sorting in the view.
     */
    List<CustomerPreference> findByCustomer_IdOrderByPreferenceTypeAscValueAsc(Long customerId);

    /** Tenant-scoped lookup: another restaurant's preference id reads as absent, not forbidden. */
    Optional<CustomerPreference> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** Backs the unique constraint's friendly error — the same fact is never recorded twice. */
    boolean existsByCustomer_IdAndPreferenceTypeAndValueIgnoreCase(
            Long customerId, CustomerPreference.Type preferenceType, String value);

    /**
     * Erasure: preferences follow the customer out.
     *
     * <p>This is the path that actually runs, not a convenience alongside the migration's
     * {@code ON DELETE CASCADE}. The JPA mapping is a plain {@code @ManyToOne}, so a schema generated
     * from the entities has a foreign key with no cascade at all — deleting the customer there fails
     * with a referential-integrity violation rather than silently orphaning rows. The cascade in V183
     * is the backstop for deletes that bypass the service; this is the guarantee.
     *
     * @return how many were erased, so the deletion is auditable in the log rather than silent.
     */
    long deleteByCustomer_Id(Long customerId);
}
