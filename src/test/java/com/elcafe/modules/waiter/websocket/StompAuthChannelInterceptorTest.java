package com.elcafe.modules.waiter.websocket;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StompAuthChannelInterceptorTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserRepository userRepository;
    @Mock private Claims claims;
    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtUtil, userRepository);
        setMode("enforce");
    }

    private void setMode(String mode) {
        ReflectionTestUtils.setField(interceptor, "mode", mode);
    }

    private Message<byte[]> connect(String authHeader) {
        StompHeaderAccessor a = StompHeaderAccessor.create(StompCommand.CONNECT);
        a.setSessionAttributes(new HashMap<>());
        a.setLeaveMutable(true); // keep the accessor live so getAccessor() works, like the real pipeline
        if (authHeader != null) a.setNativeHeader("Authorization", authHeader);
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String dest, Map<String, Object> session) {
        StompHeaderAccessor a = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        a.setSessionAttributes(session);
        a.setDestination(dest);
        a.setUser(() -> "u@t.co");
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private Message<byte[]> send(String dest) {
        StompHeaderAccessor a = StompHeaderAccessor.create(StompCommand.SEND);
        a.setSessionAttributes(new HashMap<>());
        a.setDestination(dest);
        a.setUser(() -> "u@t.co");
        return MessageBuilder.createMessage(new byte[0], a.getMessageHeaders());
    }

    private void stubValidStaffToken(String email, Long restaurantId, UserRole role) {
        when(jwtUtil.extractAllClaims("tok")).thenReturn(claims);
        when(jwtUtil.isTokenExpired("tok")).thenReturn(false);
        when(claims.get("restaurantId", Long.class)).thenReturn(null);
        when(claims.getSubject()).thenReturn(email);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(
                User.builder().email(email).restaurantId(restaurantId).role(role).build()));
    }

    @Test
    @DisplayName("ENFORCE + CONNECT without a token → rejected")
    void connect_noToken_enforce_rejected() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SHADOW + CONNECT without a token → allowed (logged, not blocked)")
    void connect_noToken_shadow_allowed() {
        setMode("shadow");
        assertThatCode(() -> interceptor.preSend(connect(null), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OFF → no-op")
    void off_noop() {
        setMode("off");
        assertThatCode(() -> interceptor.preSend(connect(null), null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE + valid staff token → CONNECT binds the session tenant")
    void connect_validToken_bindsTenant() {
        stubValidStaffToken("a@t.co", 5L, UserRole.ADMIN);
        Message<byte[]> msg = connect("Bearer tok");

        interceptor.preSend(msg, null);

        StompHeaderAccessor bound = org.springframework.messaging.support.MessageHeaderAccessor
                .getAccessor(msg, StompHeaderAccessor.class);
        Map<String, Object> attrs = bound.getSessionAttributes();
        org.assertj.core.api.Assertions.assertThat(attrs.get("ws.restaurantId")).isEqualTo(5L);
        // The CONNECT identity must persist on the live accessor (else every SUBSCRIBE looks anonymous).
        org.assertj.core.api.Assertions.assertThat(bound.getUser()).isNotNull();
        org.assertj.core.api.Assertions.assertThat(bound.getUser().getName()).isEqualTo("a@t.co");
    }

    @Test
    @DisplayName("ENFORCE + SUBSCRIBE to another tenant's print topic → rejected")
    void subscribe_crossTenant_rejected() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/print-agent/9", session), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ENFORCE + SUBSCRIBE to own tenant's order topic → allowed")
    void subscribe_ownTenant_allowed() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/restaurant/5/orders", session), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE + SUPER_ADMIN session → may subscribe to any tenant")
    void subscribe_superAdmin_allowed() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", null);
        session.put("ws.superAdmin", true);
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/print-agent/9", session), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE + SUBSCRIBE to a retired bare global topic → rejected")
    void subscribe_legacyGlobalTopic_rejected() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/kitchen", session), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/waiter/orders", session), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/table", session), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SHADOW + SUBSCRIBE to a retired bare global topic → allowed (logged, not blocked)")
    void subscribe_legacyGlobalTopic_shadow_allowed() {
        setMode("shadow");
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/kitchen", session), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE + SUBSCRIBE to own tenant's per-tenant kitchen topic → allowed")
    void subscribe_ownTenantKitchen_allowed() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatCode(() -> interceptor.preSend(subscribe("/topic/restaurant/5/kitchen", session), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ENFORCE + SUBSCRIBE to another tenant's per-tenant kitchen topic → rejected")
    void subscribe_crossTenantKitchen_rejected() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/restaurant/9/kitchen", session), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ENFORCE + SUBSCRIBE to an Ant-wildcard tenant destination → rejected (broker fans out patterns)")
    void subscribe_wildcardPattern_rejected() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", 5L);
        session.put("ws.superAdmin", false);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/restaurant/*/orders", session), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/restaurant/**", session), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/print-agent/*", session), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ENFORCE + a SUPER_ADMIN may NOT wildcard-subscribe (must name a concrete tenant)")
    void subscribe_wildcardPattern_rejectedForSuperAdmin() {
        Map<String, Object> session = new HashMap<>();
        session.put("ws.restaurantId", null);
        session.put("ws.superAdmin", true);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/restaurant/**", session), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ENFORCE + client SEND straight to a broker destination → rejected (would bypass controller routing)")
    void send_toBrokerDestination_rejected() {
        assertThatThrownBy(() -> interceptor.preSend(send("/topic/restaurant/9/orders"), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(send("/topic/kitchen"), null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(send("/queue/anything"), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("ENFORCE + client SEND to an /app application destination → allowed")
    void send_toAppDestination_allowed() {
        assertThatCode(() -> interceptor.preSend(send("/app/kitchen/order-status"), null))
                .doesNotThrowAnyException();
    }
}
