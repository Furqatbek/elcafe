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
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

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
 * to a tenant-scoped destination ({@code /topic/print-agent/{id}}, {@code /topic/restaurant/{id}/...})
 * it checks the destination tenant matches the session's — a session may only read its own tenant's stream.
 * SUBSCRIBE to a retired bare global topic ({@code /topic/kitchen}, {@code /topic/table},
 * {@code /topic/waiter/*}) is refused: those order/table/waiter streams are now published per-tenant under
 * {@code /topic/restaurant/{id}/...}, and the bare topics used to leak every tenant's live orders.
 *
 * <p>Governed by {@code app.websocket.auth.mode} (off/shadow/enforce), default <b>shadow</b>, mirroring the
 * Phase 0 tenant-enforcement rollout: it logs {@code [ws-shadow]} violations without blocking so the real
 * waiter app and the external print agent can be updated to send a token before {@code enforce} is flipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    /**
     * The only legitimate broker (pub/sub) subscriptions: a concrete, numeric, tenant-scoped destination.
     * Anything else under the {@code /topic} prefix — the retired bare globals (e.g. {@code /topic/kitchen}),
     * a stray topic, or (critically) an Ant-wildcard destination (a restaurant path with a wildcard in the
     * id slot) that the SimpleBroker's {@link AntPathMatcher} would fan out across every tenant — is refused
     * by default.
     */
    private static final Pattern TENANT_DEST =
            Pattern.compile("/topic/(?:print-agent/(\\d+)|restaurant/(\\d+)/.*)");
    /** STOMP session attribute holding the caller's tenant, read by tenant-scoped SEND handlers. */
    static final String ATTR_TENANT = "ws.restaurantId";
    private static final String ATTR_SUPERADMIN = "ws.superAdmin";
    /** Same matcher the SimpleBroker uses, to detect (and refuse) Ant-pattern subscription destinations. */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
        // Use the LIVE mutable accessor, not StompHeaderAccessor.wrap(message): setUser() on a wrap()
        // copy is discarded (its user-change callback is null), so the CONNECT identity would never
        // persist to the STOMP session and every later SUBSCRIBE would look unauthenticated. getAccessor
        // returns the mutable accessor Spring's StompSubProtocolHandler reads back per frame.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message); // read-only fallback (e.g. unit tests)
        }
        StompCommand cmd = accessor.getCommand();
        try {
            if (StompCommand.CONNECT.equals(cmd)) {
                authenticateConnect(accessor);
            } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
                authorizeSubscription(accessor);
            } else if (StompCommand.SEND.equals(cmd)) {
                authorizeSend(accessor);
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

    /**
     * Authorize a broker (pub/sub) subscription. Only {@code /topic/**} is gated — {@code /user/**} queues
     * are per-session and not tenant-broadcast. A subscription must name ONE concrete, numeric,
     * tenant-scoped destination ({@link #TENANT_DEST}) belonging to the session's own tenant; everything
     * else is refused by default (the retired bare globals, and stray topics). Ant-pattern destinations
     * are rejected outright — even for a SUPER_ADMIN — because the SimpleBroker matches subscriptions with
     * {@link AntPathMatcher}, so a restaurant path with a wildcard id (or a trailing double-star) would
     * otherwise fan every tenant's stream to one subscriber.
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest == null || !dest.startsWith("/topic/")) {
            return; // non-broker (e.g. /user/**) subscriptions are per-session, not tenant-broadcast
        }
        Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
        if (sessionAttrs == null || accessor.getUser() == null) {
            throw new AccessDeniedException("unauthenticated subscription");
        }
        if (PATH_MATCHER.isPattern(dest)) {
            throw new AccessDeniedException("pattern subscription destination is not allowed: " + dest);
        }
        if (Boolean.TRUE.equals(sessionAttrs.get(ATTR_SUPERADMIN))) {
            return; // platform operator may observe any (concrete) tenant
        }
        Matcher matcher = TENANT_DEST.matcher(dest);
        if (!matcher.matches()) {
            throw new AccessDeniedException(
                    "subscription to non-tenant-scoped topic " + dest + " is refused");
        }
        Long sessionTenant = (Long) sessionAttrs.get(ATTR_TENANT);
        String idStr = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        Long destTenant = Long.valueOf(idStr);
        if (!destTenant.equals(sessionTenant)) {
            throw new AccessDeniedException(
                    "session tenant " + sessionTenant + " may not subscribe to tenant " + destTenant);
        }
    }

    /**
     * Authorize a client SEND. Clients must publish only to application destinations ({@code /app/**},
     * handled by {@code @MessageMapping}, which derive their broadcast target from the session tenant). A
     * SEND straight to a broker pub/sub destination ({@code /topic/**}, {@code /queue/**}) is relayed to
     * subscribers with NO controller check, letting an authenticated session inject fabricated events into
     * any tenant's stream — so it is refused. Server-side broadcasts use the broker/outbound channel and
     * never pass through this inbound interceptor, so they are unaffected.
     */
    private void authorizeSend(StompHeaderAccessor accessor) {
        String dest = accessor.getDestination();
        if (dest != null && (dest.startsWith("/topic/") || dest.startsWith("/queue/"))) {
            throw new AccessDeniedException(
                    "clients may not SEND directly to broker destination " + dest + "; use /app/**");
        }
    }

    /** Minimal STOMP session principal carrying the authenticated subject. */
    private record StompPrincipal(String name) implements Principal {
        @Override public String getName() { return name; }
    }
}
