package com.elcafe.modules.auth.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/system-users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SystemUserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    private static final Set<UserRole> SYSTEM_ROLES = Set.of(
            UserRole.ADMIN, UserRole.OWNER, UserRole.MANAGER, UserRole.OPERATOR);

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAll() {
        // User is deliberately not Hibernate-@Filtered (see User.java); constrain cross-tenant
        // enumeration here at the query layer. Admin-user management is high-sensitivity (emails +
        // roles of every tenant's privileged accounts), so this scopes ALWAYS — not just after the
        // enforce flip: a tenant admin sees only their own restaurant's users, SUPER_ADMIN sees all,
        // and a caller with no assigned restaurant sees none (deny-all sentinel).
        Long tenantScope = restaurantAuthorizationService.currentTenantReadScopeStrict();
        List<User> users = (tenantScope == null
                ? userRepository.findAll()
                : userRepository.findByRestaurantId(tenantScope)).stream()
                .filter(u -> SYSTEM_ROLES.contains(u.getRole()))
                .toList();

        List<Map<String, Object>> result = users.stream().map(u -> Map.<String, Object>of(
                "id", u.getId(),
                "email", u.getEmail() != null ? u.getEmail() : "",
                "firstName", u.getFirstName() != null ? u.getFirstName() : "",
                "lastName", u.getLastName() != null ? u.getLastName() : "",
                "phone", u.getPhone() != null ? u.getPhone() : "",
                "role", u.getRole().name(),
                "active", u.getActive()
        )).toList();

        return ResponseEntity.ok(ApiResponse.success("System users retrieved", result));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(@Valid @RequestBody CreateRequest req) {
        if (!SYSTEM_ROLES.contains(req.role)) {
            throw new IllegalArgumentException("Invalid role. Must be one of: " + SYSTEM_ROLES);
        }
        if (userRepository.existsByEmail(req.email)) {
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .email(req.email)
                .password(passwordEncoder.encode(req.password))
                .firstName(req.firstName)
                .lastName(req.lastName)
                .phone(req.phone)
                .role(req.role)
                .active(true)
                .emailVerified(true)
                // Bind the new system user to the creator's restaurant once enforcement is on
                // (null for SUPER_ADMIN / pre-enforcement, preserving current behaviour).
                .restaurantId(restaurantAuthorizationService.currentTenantScopeOrNull())
                .build();

        User saved = userRepository.save(user);
        log.info("System user created: {} ({})", saved.getEmail(), saved.getRole());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created", toMap(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> update(@PathVariable Long id, @RequestBody UpdateRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Prevent cross-tenant account takeover (e.g. password reset) via a guessed id,
        // including platform/legacy (null-restaurant) accounts — see requireAccess.
        requireAccess(user);

        if (req.firstName != null) user.setFirstName(req.firstName);
        if (req.lastName != null) user.setLastName(req.lastName);
        if (req.phone != null) user.setPhone(req.phone);
        if (req.role != null && SYSTEM_ROLES.contains(req.role)) user.setRole(req.role);
        if (req.active != null) user.setActive(req.active);
        if (req.password != null && !req.password.isBlank()) {
            user.setPassword(passwordEncoder.encode(req.password));
        }
        // Revoke the target's existing tokens (access + refresh both carry tokenVersion, checked per
        // request) when an admin deactivates or resets the password — otherwise a deprovisioned or
        // taken-over account keeps working and can refresh indefinitely.
        boolean deactivating = Boolean.FALSE.equals(req.active);
        boolean passwordReset = req.password != null && !req.password.isBlank();
        if (deactivating || passwordReset) {
            bumpTokenVersion(user);
        }

        User saved = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User updated", toMap(saved)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        // Prevent cross-tenant deactivation via a guessed id (incl. null-restaurant accounts).
        requireAccess(user);
        user.setActive(false);
        bumpTokenVersion(user); // kill the deactivated account's live tokens immediately
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("User deactivated", null));
    }

    /** Bump the revocation counter so every already-issued access/refresh token for this user fails
     *  the per-request {@code tokenVersion} check in {@code JwtUtil.validateToken}. */
    private void bumpTokenVersion(User user) {
        int current = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        user.setTokenVersion(current + 1);
    }

    /**
     * Authorize a mutation on {@code user}. Beyond the standard tenant check, a platform/legacy
     * account ({@code restaurant_id IS NULL}) must only be reachable by the cross-tenant operator:
     * {@link RestaurantAuthorizationService#checkAccess(Long)} treats a null restaurantId as an
     * unconstrained "aggregate" load, so without this guard any tenant admin could mutate such an
     * account via a guessed id. Independent of enforcement mode (it is an authorization decision on
     * the target, not a Hibernate-filter concern).
     */
    private void requireAccess(User user) {
        // isAdmin() == SUPER_ADMIN, the only cross-tenant role (see RestaurantAuthorizationService).
        if (user.getRestaurantId() == null && !restaurantAuthorizationService.isAdmin()) {
            throw new AccessDeniedException("Access denied: platform account is operator-only");
        }
        // ALWAYS-enforce (not the mode-aware checkAccess): cross-tenant password reset / role
        // escalation / deactivation of another tenant's admin is never legitimate and must not wait
        // for the shadow→enforce flip. SUPER_ADMIN bypasses; same-tenant always passes.
        restaurantAuthorizationService.validateRestaurantAccess(user.getRestaurantId());
    }

    private Map<String, Object> toMap(User u) {
        return Map.of(
                "id", u.getId(),
                "email", u.getEmail(),
                "firstName", u.getFirstName() != null ? u.getFirstName() : "",
                "lastName", u.getLastName() != null ? u.getLastName() : "",
                "phone", u.getPhone() != null ? u.getPhone() : "",
                "role", u.getRole().name(),
                "active", u.getActive()
        );
    }

    public record CreateRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String firstName,
            @NotBlank String lastName,
            String phone,
            @NotNull UserRole role
    ) {}

    public record UpdateRequest(
            String firstName,
            String lastName,
            String phone,
            String password,
            UserRole role,
            Boolean active
    ) {}
}
