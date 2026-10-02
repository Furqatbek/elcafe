package com.elcafe.modules.settings.service;

import com.elcafe.modules.settings.dto.PrintAgentStatusResponse;
import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.elcafe.modules.settings.websocket.PrintAgentWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Answers one question for a venue: are kitchen tickets coming out of the printer?
 *
 * <p>Nothing in the cloud can see a printer. What it can see is an agent still talking to us and a
 * queue that is or is not draining, and the state here is those two facts combined. The combination
 * matters because each alone misleads in a different direction: an agent connected to a printer with
 * no paper reports a healthy connection while nothing prints, and a venue that has had no orders since
 * opening has an empty queue whether or not anything works.
 *
 * <p>Which is why {@code ONLINE} is never inferred from an empty queue, and {@code BACKLOG} is a
 * separate state from {@code OFFLINE} rather than being folded into it. They need different actions:
 * one is "go and look at the printer", the other is "go and look at the computer".
 */
@Service
@RequiredArgsConstructor
public class PrintAgentStatusService {

    /**
     * How long a ticket may sit unprinted before a connected agent is reported as having a problem.
     *
     * <p>Generous on purpose. A ticket is created the moment an order is, and it is normal for one to
     * be in flight for a few seconds; a venue told something is wrong every time the kitchen is busy
     * will stop reading the indicator, which costs more than the warning is worth.
     */
    @Value("${app.printing.backlog-after-minutes:5}")
    private long backlogAfterMinutes;

    private final PrintAgentWebSocketHandler printAgentHandler;
    private final PrintJobRepository printJobRepository;

    @Transactional(readOnly = true)
    public PrintAgentStatusResponse statusFor(Long restaurantId) {
        Optional<PrintAgentWebSocketHandler.ConnectedAgent> agent =
                printAgentHandler.liveAgentFor(restaurantId);

        long queued = printJobRepository.countUnprinted(restaurantId);
        long deadLettered = printJobRepository.countByRestaurant_IdAndStatus(
                restaurantId, PrintJob.PrintJobStatus.DEAD_LETTER);

        LocalDateTime oldest = printJobRepository.oldestUnprintedAt(restaurantId);
        Long oldestMinutes = oldest == null
                ? null
                : Math.max(0, Duration.between(oldest, LocalDateTime.now()).toMinutes());

        PrintAgentStatusResponse.PrintAgentStatusResponseBuilder status = PrintAgentStatusResponse.builder()
                .queuedJobs(queued)
                .deadLetterJobs(deadLettered)
                .oldestQueuedAt(oldest == null ? null : oldest.atZone(ZoneId.systemDefault()).toOffsetDateTime())
                .oldestQueuedMinutes(oldestMinutes)
                .backlogAfterMinutes(backlogAfterMinutes);

        if (agent.isEmpty()) {
            return status.state(PrintAgentStatusResponse.State.OFFLINE).build();
        }

        PrintAgentWebSocketHandler.ConnectedAgent live = agent.get();
        status.agentId(live.agentId())
                .connectedAt(live.connectedAt())
                .lastSeenAt(live.lastSeenAt())
                .secondsSinceLastSeen(
                        Math.max(0, Duration.between(live.lastSeenAt(), OffsetDateTime.now()).toSeconds()));

        if (!live.isFresh()) {
            // Still in the map because nothing told us it went away. Reported as not printing, because
            // that is the safe reading and because the alternative is a green light over a dead kitchen.
            return status.state(PrintAgentStatusResponse.State.STALE).build();
        }

        boolean behind = oldestMinutes != null && oldestMinutes >= backlogAfterMinutes;
        return status.state(behind
                        ? PrintAgentStatusResponse.State.BACKLOG
                        : PrintAgentStatusResponse.State.ONLINE)
                .build();
    }
}
