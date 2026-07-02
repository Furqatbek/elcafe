package com.elcafe.modules.waiter.websocket;

import com.elcafe.common.tenant.TenantEnforcementMode;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP inbound-channel guard (audit #21). Previously the WebSocket layer had NO app-level auth: anyone
 * could CONNECT unauthenticated and SUBSCRIBE to any tenant's live order / print-job topics, and drive
 * the print-job @MessageMapping methods. Servlet filters never see STOMP frames, so this is enforced here.
 *
 * <p>On CONNECT it requires a valid Bearer JWT and binds the caller's tenant to the session. On SUBSCRIBE
 * to a tenant-scoped destination ({@code /topic/print-agent/{id}}, {@code /topic/restaurant/{id}/orders})
 * it checks the destination tenant matches the session's — a session may only read its own tenant's stream.
 *
 * <p>Governed by {@code app.websocket.auth.mode} (off/shadow/enforce), default <b>shadow</b>, mirroring the
 * Phase 0 tenant-enforcement rollout: it logs {@code [ws-shadow]} violations without blocking so the real
 * waiter app and the external print agent can be updated to send a token before {@code enforce} is flipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TENANT_DEST =
            Pattern.compile("/topic/(?:print-agent/(\\d+)|restaurant/(\\d+)/.*)");
    private static final String ATTR_TENANT = "ws.restaurantId";
    private static final String ATTR_SUPERADMIN = "ws.superAdmin";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.websocket.auth.mode:shadow}")
    private String mode;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        TenantEnforcementMode m = TenantEnforcementMode.from(mode);
        if (m == TenantEnforcementMode.OFF) {
            return message;
        }
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand cmd = accessor.getCommand();
        try {
            if (StompCommand.CONNECT.equals(cmd)) {
                authenticateConnect(accessor);
            } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
                authorizeSubscription(accessor);
            }
        } catch (AccessDeniedException e) {
            if (m == TenantEnforcementMode.ENFORCE) {
                throw e; // reject the frame
            }
            log.warn("[ws-shadow] would block {} {}: {}", cmd,
                    accessor.getDestination() == null ? "" : accessor.getDestination(), e.getMessage());
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        String token = (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
        if (token == null) {
            throw new AccessDeniedException("missing bearer token on CONNECT");
        }
        final Claims claims;
        try {
            claims = jwtUtil.extractAllClaims(token);
        } catch (Exception e) {
            throw new AccessDeniedException("invalid token: " + e.getMessage());
        }
        if (jwtUtil.isTokenExpired(token)) {
            throw new AccessDeniedException("expired token");
        }
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        // Resolve the caller's tenant: waiter/consumer tokens carry restaurantId; staff resolve from DB.
        Long restaurantId = claims.get("restaurantId", Long.class);
        boolean superAdmin = false;
        if (restaurantId == null) {
            User user = userRepository.findByEmail(claims.getSubject()).orElse(null);
            if (user != null) {
                restaurantId = user.getRestaurantId();
                superAdmin = user.getRole() == UserRole.SUPER_ADMIN;
            }
        }
        if (sessionAttrs != null) {
            sessionAttrs.put(ATTR_TENANT, restaurantId);
            sessionAttrs.put(ATTR_SUPERADMIN, superAdmin);
        }
        accessor.setUser(new StompPrincipal(claims.getSubject()));
        log.debug("[ws] authenticated CONNECT for {} (tenant={}, superAdmin={})",
                claims.getSubject(), restaurantId, superAdmin);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null) {
            return;
        }
        Matcher matcher = TENANT_DEST.matcher(dest);
        if (!matcher.matches()) {
            return; // not a tenant-scoped destination (global topics handled separately)
        }
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null || accessor.getUser() == null) {
            throw new AccessDeniedException("unauthenticated subscription");
        }
        if (Boolean.TRUE.equals(sessionAttrs.get(ATTR_SUPERADMIN))) {
            return; // platform operator may observe any tenant
        }
        Long sessionTenant = (Long) sessionAttrs.get(ATTR_TENANT);
        String idStr = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        Long destTenant = Long.valueOf(idStr);
        if (!destTenant.equals(sessionTenant)) {
            throw new AccessDeniedException(
                    "session tenant " + sessionTenant + " may not subscribe to tenant " + destTenant);
        }
    }

    /** Minimal STOMP session principal carrying the authenticated subject. */
    private record StompPrincipal(String name) implements Principal {
        @Override public String getName() { return name; }
    }
}
