package com.elcafe.modules.settings.service;

import com.elcafe.modules.settings.dto.PrintAgentStatusResponse;
import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.elcafe.modules.settings.websocket.PrintAgentWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The indicator a venue reads to answer "are tickets coming out of the printer".
 *
 * <p>Its whole value is that it does not lie in the reassuring direction. A green light over a dead
 * kitchen is worse than no light at all, because it sends nobody to look — so the cases worth pinning
 * are the ones where something is broken and the obvious implementation would still say fine.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintAgentStatusServiceTest {

    private static final Long VENUE = 3L;

    @Mock private PrintAgentWebSocketHandler handler;
    @Mock private PrintJobRepository printJobRepository;

    private PrintAgentStatusService service;

    @BeforeEach
    void setUp() {
        service = new PrintAgentStatusService(handler, printJobRepository);
        ReflectionTestUtils.setField(service, "backlogAfterMinutes", 5L);
        when(printJobRepository.countUnprinted(anyLong())).thenReturn(0L);
        when(printJobRepository.countByRestaurant_IdAndStatus(anyLong(), any())).thenReturn(0L);
        when(printJobRepository.oldestUnprintedAt(anyLong())).thenReturn(null);
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.empty());
        when(handler.lastKnownTokenExpiry(anyLong())).thenReturn(Optional.empty());
    }

    private PrintAgentWebSocketHandler.ConnectedAgent agentLastSeen(OffsetDateTime lastSeen) {
        return agentLastSeen(lastSeen, null);
    }

    private PrintAgentWebSocketHandler.ConnectedAgent agentLastSeen(
            OffsetDateTime lastSeen, java.time.Instant tokenExpiresAt) {
        return new PrintAgentWebSocketHandler.ConnectedAgent(
                "agent-abc", VENUE, "sess-1", lastSeen.minusHours(2), lastSeen, tokenExpiresAt);
    }

    @Test
    @DisplayName("an agent heard from a moment ago, with nothing queued, is online")
    void freshAgent_isOnline() {
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.of(agentLastSeen(OffsetDateTime.now())));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getState()).isEqualTo(PrintAgentStatusResponse.State.ONLINE);
        assertThat(status.getAgentId()).isEqualTo("agent-abc");
        assertThat(status.getSecondsSinceLastSeen()).isNotNull().isLessThan(5L);
    }

    @Test
    @DisplayName("an agent that stopped talking reads STALE, not online, however it left the map")
    void silentAgent_isStale() {
        // The case this feature exists for: the kitchen machine was switched off at the wall, so no
        // DISCONNECT was ever sent and the socket may never have closed. Its entry is still here.
        // Reporting that as connected is the one outcome that makes the indicator worse than nothing.
        when(handler.liveAgentFor(VENUE))
                .thenReturn(Optional.of(agentLastSeen(OffsetDateTime.now().minusMinutes(10))));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getState()).isEqualTo(PrintAgentStatusResponse.State.STALE);
        // Still named, because knowing which agent went quiet is how somebody finds the right machine.
        assertThat(status.getAgentId()).isEqualTo("agent-abc");
    }

    @Test
    @DisplayName("no agent at all is offline, and says how many tickets are waiting")
    void noAgent_isOffline() {
        when(printJobRepository.countUnprinted(VENUE)).thenReturn(7L);
        when(printJobRepository.oldestUnprintedAt(VENUE))
                .thenReturn(LocalDateTime.now().minusMinutes(42));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getState()).isEqualTo(PrintAgentStatusResponse.State.OFFLINE);
        assertThat(status.getQueuedJobs()).isEqualTo(7);
        assertThat(status.getOldestQueuedMinutes()).isBetween(41L, 43L);
        assertThat(status.getAgentId()).isNull();
    }

    @Test
    @DisplayName("a connected agent with tickets stuck behind it is BACKLOG, not ONLINE")
    void connectedButNotPrinting_isBacklog() {
        // The printer is out of paper or jammed. The agent is perfectly healthy and will keep saying
        // so, which is why connection state alone cannot answer the question anybody is really asking.
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.of(agentLastSeen(OffsetDateTime.now())));
        when(printJobRepository.countUnprinted(VENUE)).thenReturn(4L);
        when(printJobRepository.oldestUnprintedAt(VENUE))
                .thenReturn(LocalDateTime.now().minusMinutes(20));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getState()).isEqualTo(PrintAgentStatusResponse.State.BACKLOG);
        assertThat(status.getQueuedJobs()).isEqualTo(4);
    }

    @Test
    @DisplayName("a ticket in flight for a few seconds is not a backlog")
    void briefQueue_staysOnline() {
        // A ticket exists from the moment an order does. Warning every time the kitchen is busy would
        // teach everyone to ignore the indicator, which costs more than the warning is worth.
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.of(agentLastSeen(OffsetDateTime.now())));
        when(printJobRepository.countUnprinted(VENUE)).thenReturn(2L);
        when(printJobRepository.oldestUnprintedAt(VENUE))
                .thenReturn(LocalDateTime.now().minusSeconds(20));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getState()).isEqualTo(PrintAgentStatusResponse.State.ONLINE);
        assertThat(status.getQueuedJobs()).isEqualTo(2);
    }

    @Test
    @DisplayName("tickets we have given up on are counted separately from ones still trying")
    void deadLetters_areTheirOwnNumber() {
        // A ticket that exhausted its retries will never print on its own. Folding it into the waiting
        // count would have a venue waiting for something nobody is going to send.
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.of(agentLastSeen(OffsetDateTime.now())));
        when(printJobRepository.countByRestaurant_IdAndStatus(
                VENUE, PrintJob.PrintJobStatus.DEAD_LETTER)).thenReturn(3L);

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getDeadLetterJobs()).isEqualTo(3);
        assertThat(status.getQueuedJobs()).isZero();
    }

    @Test
    @DisplayName("the screen is told the threshold rather than hardcoding it")
    void thresholdIsPublished() {
        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getBacklogAfterMinutes()).isEqualTo(5);
    }

    @Test
    @DisplayName("a connected agent reports how long its key has left, so it can be renewed in time")
    void connectedAgent_reportsKeyLifetime() {
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.of(
                agentLastSeen(OffsetDateTime.now(), Instant.now().plus(Duration.ofDays(20)))));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getTokenExpiresInDays()).isBetween(19L, 20L);
        assertThat(status.isTokenExpired()).isFalse();
        assertThat(status.getTokenExpiresAt()).isNotNull();
    }

    @Test
    @DisplayName("an absent agent whose key ran out is named as that, not as a missing computer")
    void expiredKey_isNamedEvenWithNoAgentConnected() {
        // The failure that arrives with nothing having changed: the machine is on, the agent is
        // running, and a year-old credential quietly ran out. Without this it reads as OFFLINE and
        // somebody spends an hour confirming the computer is switched on.
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.empty());
        when(handler.lastKnownTokenExpiry(VENUE))
                .thenReturn(Optional.of(Instant.now().minus(Duration.ofDays(2))));

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getState()).isEqualTo(PrintAgentStatusResponse.State.OFFLINE);
        assertThat(status.isTokenExpired()).isTrue();
        assertThat(status.getTokenExpiresInDays()).isNegative();
    }

    @Test
    @DisplayName("a venue we have never seen an agent for claims nothing about its key")
    void unknownKey_staysNull() {
        // The expiry lives inside a token somebody else is holding, not in our database, so after a
        // restart we genuinely do not know. Guessing would be worse than silence.
        when(handler.liveAgentFor(VENUE)).thenReturn(Optional.empty());
        when(handler.lastKnownTokenExpiry(VENUE)).thenReturn(Optional.empty());

        PrintAgentStatusResponse status = service.statusFor(VENUE);

        assertThat(status.getTokenExpiresAt()).isNull();
        assertThat(status.getTokenExpiresInDays()).isNull();
        assertThat(status.isTokenExpired()).isFalse();
    }
}
