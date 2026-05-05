package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShiftEnforcementService {

    private final EmployeeShiftRepository shiftRepository;
    private final JwtUtil jwtUtil;

    public void requireActiveShift() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return;

        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        if (isAdmin) return;

        EmployeeShift shift = getActiveShiftForCurrentUser();
        if (shift == null) {
            throw new IllegalStateException("No active shift. Please clock in before performing this operation.");
        }
    }

    public EmployeeShift getActiveShiftForCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;

        // Try employee (User) shift first
        Long userId = getUserId(auth);
        if (userId != null) {
            Optional<EmployeeShift> shift = shiftRepository.findActiveShiftByEmployee(userId);
            if (shift.isPresent()) return shift.get();
        }

        // Try waiter shift
        Long waiterId = getWaiterIdFromToken();
        if (waiterId != null) {
            Optional<EmployeeShift> shift = shiftRepository.findActiveShiftByWaiter(waiterId);
            if (shift.isPresent()) return shift.get();
        }

        return null;
    }

    private Long getUserId(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof com.elcafe.security.UserPrincipal up) {
            return up.getId();
        }
        return null;
    }

    private Long getWaiterIdFromToken() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest request = attrs.getRequest();
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) return null;
            String jwt = authHeader.substring(7);
            io.jsonwebtoken.Claims claims = jwtUtil.extractAllClaims(jwt);
            return claims.get("waiterId", Long.class);
        } catch (Exception e) {
            return null;
        }
    }
}
