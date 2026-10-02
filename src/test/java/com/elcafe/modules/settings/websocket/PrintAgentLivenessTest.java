package com.elcafe.modules.settings.websocket;

import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Whether we actually know an agent is alive.
 *
 * <p>A print agent lives on a machine in a kitchen, and the ways it stops working are mostly ways that
 * send us nothing: the plug is pulled, the router dies, the process is killed. The map of connected
 * agents was therefore append-mostly — an entry went in on connect and only came out if the agent was
 * polite enough to say goodbye.
 *
 * <p>Two mechanisms replace that, and both are here: the session disconnect, which catches everything
 * that closes a socket, and the staleness window, which catches the half-open connection that never
 * closes at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintAgentLivenessTest {

    private static final Long VENUE = 3L;

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private PrintJobRepository printJobRepository;

    private PrintAgentWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PrintAgentWebSocketHandler(messagingTemplate, printJobRepository, new ObjectMapper());
        when(printJobRepository.findPendingJobsByRestaurant(anyLong())).thenReturn(List.of());
    }

    @Test
    @DisplayName("a freshly registered agent counts as connected")
    void registered_isConnected() {
        handler.registerAgent("agent-1", VENUE, "sess-1");

        assertThat(handler.hasConnectedAgent(VENUE)).isTrue();
        assertThat(handler.getAgentForRestaurant(VENUE)).isEqualTo("agent-1");
        assertThat(handler.getConnectedAgentCount(VENUE)).isEqualTo(1);
    }

    @Test
    @DisplayName("a dropped session unregisters the agent, which is how a pulled plug is noticed")
    void droppedSession_unregisters() {
        handler.registerAgent("agent-1", VENUE, "sess-1");

        handler.unregisterSession("sess-1");

        // Nothing was sent by the agent. This path is the only one that fires when a kitchen machine
        // is switched off at the wall, and without it the entry would outlive the server.
        assertThat(handler.hasConnectedAgent(VENUE)).isFalse();
        assertThat(handler.getAgentForRestaurant(VENUE)).isNull();
    }

    @Test
    @DisplayName("somebody else's session disconnecting leaves the agent alone")
    void unrelatedSession_isIgnored() {
        handler.registerAgent("agent-1", VENUE, "sess-1");

        // Most disconnects on this server belong to the admin and waiter sockets.
        handler.unregisterSession("sess-waiter-99");
        handler.unregisterSession(null);

        assertThat(handler.hasConnectedAgent(VENUE)).isTrue();
    }

    @Test
    @DisplayName("an agent we have not heard from is not connected, even though it is still in the map")
    void silentAgent_isNotConnected() {
        handler.registerAgent("agent-1", VENUE, "sess-1");
        ageLastSeen("agent-1", OffsetDateTime.now().minusMinutes(10));

        // The half-open socket: nothing closed, so no disconnect fired, so the entry is still here.
        // Presence is not evidence of life, and this is the assertion that keeps the indicator honest.
        assertThat(handler.hasConnectedAgent(VENUE)).isFalse();
        assertThat(handler.getConnectedAgentCount(VENUE)).isZero();
        // Still findable, so the status screen can say which agent went quiet and when.
        assertThat(handler.liveAgentFor(VENUE)).isPresent();
    }

    @Test
    @DisplayName("a heartbeat brings a stale agent back without reconnecting it")
    void heartbeat_refreshesLiveness() {
        handler.registerAgent("agent-1", VENUE, "sess-1");
        ageLastSeen("agent-1", OffsetDateTime.now().minusMinutes(10));
        assertThat(handler.hasConnectedAgent(VENUE)).isFalse();

        handler.touchAgent("agent-1");

        // An idle agent sends no jobs for hours, and STOMP's own heartbeats never reach the
        // application, so this frame is the only thing separating "quiet" from "dead".
        assertThat(handler.hasConnectedAgent(VENUE)).isTrue();
    }

    @Test
    @DisplayName("touching an agent that was never registered does nothing")
    void touchUnknown_isHarmless() {
        handler.touchAgent("agent-nobody-registered");

        assertThat(handler.hasConnectedAgent(VENUE)).isFalse();
    }

    @Test
    @DisplayName("the freshest agent wins when a venue has two, so a restart is not reported as dead")
    void twoAgents_reportTheFreshest() {
        // A restarted agent gets a new id. The old entry lingers until its socket closes, and reporting
        // the stale one would show a venue as broken moments after it recovered.
        handler.registerAgent("agent-old", VENUE, "sess-1");
        ageLastSeen("agent-old", OffsetDateTime.now().minusMinutes(10));
        handler.registerAgent("agent-new", VENUE, "sess-2");

        assertThat(handler.getAgentForRestaurant(VENUE)).isEqualTo("agent-new");
        assertThat(handler.hasConnectedAgent(VENUE)).isTrue();
    }

    @Test
    @DisplayName("another venue's agent is never this venue's")
    void otherVenue_doesNotCount() {
        handler.registerAgent("agent-1", 99L, "sess-1");

        assertThat(handler.hasConnectedAgent(VENUE)).isFalse();
        assertThat(handler.liveAgentFor(VENUE)).isEmpty();
    }

    /** Rewinds an agent's lastSeenAt, standing in for time passing with the agent saying nothing. */
    @SuppressWarnings("unchecked")
    private void ageLastSeen(String agentId, OffsetDateTime lastSeen) {
        var agents = (java.util.Map<String, PrintAgentWebSocketHandler.ConnectedAgent>)
                org.springframework.test.util.ReflectionTestUtils.getField(handler, "connectedAgents");
        PrintAgentWebSocketHandler.ConnectedAgent current = agents.get(agentId);
        agents.put(agentId, new PrintAgentWebSocketHandler.ConnectedAgent(
                current.agentId(), current.restaurantId(), current.sessionId(),
                current.connectedAt(), lastSeen));
    }
}
