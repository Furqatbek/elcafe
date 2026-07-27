package com.elcafe.modules.auth.repository;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    Optional<User> findByResetToken(String resetToken);

    Page<User> findByRole(UserRole role, Pageable pageable);

    /** Tenant-scoped role listing (§3.3 — User is not @Filter'd, so scope at the query layer). */
    Page<User> findByRoleAndRestaurantId(UserRole role, Long restaurantId, Pageable pageable);

    Page<User> findByRoleAndActiveTrue(UserRole role, Pageable pageable);

    List<User> findByRoleNotInAndActiveTrue(Collection<UserRole> excludedRoles);

    List<User> findByRoleNotIn(Collection<UserRole> excludedRoles);

    /**
     * Accounts stuck in the deny-all state: a tenant-scoped role with no restaurant.
     *
     * <p>These sign in successfully and then see an empty application, which reads as a wiped database
     * rather than a broken binding. The write paths can no longer create them
     * ({@link com.elcafe.common.security.UserTenantBinding}), but rows predating that fix still exist —
     * this is what the boot-time audit reports so they name themselves instead of being diagnosed.
     */
    @Query("SELECT u FROM User u WHERE u.restaurantId IS NULL AND u.role <> "
            + "com.elcafe.modules.auth.enums.UserRole.SUPER_ADMIN")
    List<User> findUnboundTenantScopedAccounts();

    List<User> findByRestaurantId(Long restaurantId);

    List<User> findByRestaurantIdAndRoleNotIn(Long restaurantId, Collection<UserRole> excludedRoles);
}
