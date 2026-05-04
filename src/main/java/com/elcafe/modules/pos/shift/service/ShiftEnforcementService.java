package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Enforces that operators and waiters have an active shift before performing operations.
 * Admins are exempt from shift enforcement.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftEnforcementService {

    private final EmployeeShiftRepository shiftRepository;

    /**
     * Verify the current user has an active shift. Throws if not.
     * Admins are exempt — they don't need a shift.
     */
    public void requireActiveShift() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;

        // Admins are exempt
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        if (isAdmin) return;

        // Find user ID from principal
        Long userId = getUserId(auth);
        if (userId == null) return;

        Optional<EmployeeShift> activeShift = shiftRepository.findActiveShiftByEmployee(userId);
        if (activeShift.isEmpty()) {
            throw new IllegalStateException("No active shift. Please clock in before performing this operation.");
        }
    }

    /**
     * Get the active shift for the current user, or null.
     */
    public EmployeeShift getActiveShiftForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        Long userId = getUserId(auth);
        if (userId == null) return null;

        return shiftRepository.findActiveShiftByEmployee(userId).orElse(null);
    }

    private Long getUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof com.elcafe.security.UserPrincipal up) {
            return up.getId();
        }
        return null;
    }
}
