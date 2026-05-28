package com.elcafe.modules.auth.repository;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Page<User> findByRoleAndActiveTrue(UserRole role, Pageable pageable);

    List<User> findByRoleNotInAndActiveTrue(Collection<UserRole> excludedRoles);

    List<User> findByRoleNotIn(Collection<UserRole> excludedRoles);
}
