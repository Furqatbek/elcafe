package com.elcafe.modules.settings.websocket;

import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintAgentWebSocketHandler {

    /**
     * How long an agent may go unheard before we stop calling it connected.
     *
     * <p>A kitchen that loses power or whose router dies may never send a DISCONNECT and may never
     * close the socket cleanly — the server is left holding a half-open connection. Presence in the
     * map is therefore not evidence of life, and an indicator built on presence alone would show a
     * green light over a dead printer while tickets piled up, which is worse than showing nothing.
     *
     * <p>The agent pings every 30 seconds, so two missed pings is the threshold. Short enough that a
     * venue finds out inside a couple of minutes; long enough that one dropped packet is not an alarm.
     */
    static final Duration SILENCE_BEFORE_STALE = Duration.ofSeconds(90);

    /**
     * How old a ticket may be and still be handed to an agent that has just connected.
     *
     * <p>Same property the sweep that retires them reads, so the two cannot disagree about what is
     * still worth printing.
     */
    @Value("${app.printing.unprinted-expire-after-hours:24}")
    private long unprintedExpireAfterHours;

    private final SimpMessagingTemplate messagingTemplate;
    private final PrintJobRepository printJobRepository;
    private final ObjectMapper objectMapper;

    /**
     * One live agent. Keyed by agent id; {@code sessionId} is what the disconnect listener has to hand,
     * so it is carried here rather than looked up.
     */
    public record ConnectedAgent(String agentId, Long restaurantId, String sessionId,
                                 OffsetDateTime connectedAt, OffsetDateTime lastSeenAt,
                                 Instant tokenExpiresAt) {

        ConnectedAgent seenNow() {
            return new ConnectedAgent(agentId, restaurantId, sessionId, connectedAt,
                    OffsetDateTime.now(), tokenExpiresAt);
        }

        public boolean isFresh() {
            return Duration.between(lastSeenAt, OffsetDateTime.now()).compareTo(SILENCE_BEFORE_STALE) <= 0;
        }
    }

    private final Map<String, ConnectedAgent> connectedAgents = new ConcurrentHashMap<>();

    /**
     * The last token expiry we saw for each venue, kept after the agent goes away.
     *
     * <p>An agent token lasts a year, so the day it runs out the agent is running perfectly and simply
     * cannot connect — which is indistinguishable from a switched-off machine unless something remembers
     * when the credential was due to end. This does, and it is written at CONNECT, which necessarily
     * happened while the token was still valid.
     *
     * <p>Lost on restart, and then a venue falls back to being told only that nothing is connected. That
     * is the old behaviour rather than a new failure, and the alternative is a column for a fact that is
     * already inside a token somebody is holding.
     */
    private final Map<Long, Instant> lastKnownTokenExpiry = new ConcurrentHashMap<>();

    /**
     * Register a print agent connection
     */
    public void registerAgent(String agentId, Long restaurantId) {
        registerAgent(agentId, restaurantId, null, null);
    }

    /** @param sessionId the STOMP session, so a dropped transport can unregister this agent */
    public void registerAgent(String agentId, Long restaurantId, String sessionId) {
        registerAgent(agentId, restaurantId, sessionId, null);
    }

    /**
     * @param sessionId      the STOMP session, so a dropped transport can unregister this agent
     * @param tokenExpiresAt when this agent's credential runs out, so it can be renewed in advance
     */
    public void registerAgent(String agentId, Long restaurantId, String sessionId,
                              Instant tokenExpiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        connectedAgents.put(agentId,
                new ConnectedAgent(agentId, restaurantId, sessionId, now, now, tokenExpiresAt));
        if (tokenExpiresAt != null) {
            lastKnownTokenExpiry.put(restaurantId, tokenExpiresAt);
        }
        log.info("Print agent registered: {} for restaurant {}", agentId, restaurantId);

        // Send any pending jobs immediately
        sendPendingJobs(agentId, restaurantId);
    }

    /** When this venue's agent credential runs out, as last seen — even if it is no longer connected. */
    public Optional<Instant> lastKnownTokenExpiry(Long restaurantId) {
        return Optional.ofNullable(lastKnownTokenExpiry.get(restaurantId));
    }

    /**
     * An agent said something, so it was alive a moment ago.
     *
     * <p>Called from every frame an agent sends — its heartbeat, a job acknowledgement, a request for
     * work. Deliberately not inferred from STOMP heartbeats: those are handled by the broker and never
     * reach a controller, so an idle agent would drift into looking dead.
     */
    public void touchAgent(String agentId) {
        connectedAgents.computeIfPresent(agentId, (id, agent) -> agent.seenNow());
    }

    /**
     * Unregister a print agent connection
     */
    public void unregisterAgent(String agentId) {
        ConnectedAgent removed = connectedAgents.remove(agentId);
        if (removed != null) {
            log.info("Print agent unregistered: {} (restaurant {})", agentId, removed.restaurantId());
        }
    }

    /**
     * Drop whichever agent held this STOMP session.
     *
     * <p>The path that matters: an agent that crashes, is unplugged or loses its network never sends a
     * DISCONNECT frame. Spring still raises a disconnect event when the transport closes, and this is
     * what turns that into an accurate indicator rather than a stale entry nobody ever removes.
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        connectedAgents.values().stream()
                .filter(agent -> sessionId.equals(agent.sessionId()))
                .findFirst()
                .ifPresent(agent -> {
                    connectedAgents.remove(agent.agentId());
                    log.info("Print agent {} dropped with session {} (restaurant {})",
                            agent.agentId(), sessionId, agent.restaurantId());
                });
    }

    /** The freshest live agent for a venue, if one is still being heard from. */
    public Optional<ConnectedAgent> liveAgentFor(Long restaurantId) {
        return connectedAgents.values().stream()
                .filter(agent -> agent.restaurantId().equals(restaurantId))
                .max(java.util.Comparator.comparing(ConnectedAgent::lastSeenAt));
    }

    /**
     * Check if any agent is connected for a restaurant
     *
     * <p>Freshness is part of the answer. An agent we have not heard from in
     * {@link #SILENCE_BEFORE_STALE} is reported as absent even though its entry is still here, because
     * the question every caller is really asking is "will a ticket get printed".
     */
    public boolean hasConnectedAgent(Long restaurantId) {
        return liveAgentFor(restaurantId).filter(ConnectedAgent::isFresh).isPresent();
    }

    /**
     * Get agent ID for a restaurant (returns first connected agent)
     */
    public String getAgentForRestaurant(Long restaurantId) {
        return liveAgentFor(restaurantId).map(ConnectedAgent::agentId).orElse(null);
    }

    /**
     * Notify all agents for a restaurant about new print jobs
     */
    public void notifyNewJobs(Long restaurantId) {
        String destination = "/topic/print-agent/" + restaurantId;
        PrintAgentMessage message = new PrintAgentMessage("NEW_JOBS", "New print jobs available");
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Notified print agents for restaurant {} about new jobs", restaurantId);
    }

    /**
     * Tell every venue with a live agent that there is work waiting.
     *
     * <p>For the sweeps, which re-queue jobs without knowing whose they are. Addressed only to venues
     * actually being heard from, so a re-queue does not fan out messages at topics nobody is reading.
     */
    public void notifyAllAgents() {
        connectedAgents.values().stream()
                .filter(ConnectedAgent::isFresh)
                .map(ConnectedAgent::restaurantId)
                .distinct()
                .forEach(this::notifyNewJobs);
    }

    /**
     * Hand a newly connected agent the work that is still worth doing.
     *
     * <p>Bounded by age on purpose. An agent connects after an outage and this is what it receives, so
     * without a cutoff a kitchen that was offline since lunch comes back to a printer working through
     * every ticket since, for food that was served hours ago — and the real tickets arrive behind them.
     */
    public void sendPendingJobs(String agentId, Long restaurantId) {
        List<PrintJob> pendingJobs = printJobRepository.findPendingJobsByRestaurantSince(
                restaurantId, LocalDateTime.now().minusHours(unprintedExpireAfterHours));

        if (!pendingJobs.isEmpty()) {
            String destination = "/topic/print-agent/" + restaurantId;
            for (PrintJob job : pendingJobs) {
                PrintJobMessage jobMessage = PrintJobMessage.fromEntity(job);
                messagingTemplate.convertAndSend(destination, jobMessage);
                log.debug("Sent print job {} to agent {}", job.getId(), agentId);
            }
        }
    }

    /**
     * Send a specific print job to agents for a restaurant
     */
    public void sendPrintJob(PrintJob job) {
        Long restaurantId = job.getRestaurant().getId();
        String destination = "/topic/print-agent/" + restaurantId;
        PrintJobMessage jobMessage = PrintJobMessage.fromEntity(job);
        messagingTemplate.convertAndSend(destination, jobMessage);
        log.info("Sent print job {} to restaurant {} agents", job.getId(), restaurantId);
    }

    /**
     * Get count of connected agents
     */
    public int getConnectedAgentCount() {
        return connectedAgents.size();
    }

    /**
     * Get count of connected agents for a restaurant
     */
    public int getConnectedAgentCount(Long restaurantId) {
        return (int) connectedAgents.values().stream()
                .filter(agent -> agent.restaurantId().equals(restaurantId))
                .filter(ConnectedAgent::isFresh)
                .count();
    }

    /**
     * Message sent to print agents
     */
    public record PrintAgentMessage(String type, String message) {}

    /**
     * Print job message for WebSocket transmission
     */
    public record PrintJobMessage(
            Long id,
            Long printerId,
            String printerName,
            String printerIp,
            Integer printerPort,
            String connectionType,
            String jobType,
            Long orderId,
            String orderNumber,
            String stationName,
            String printData,
            Integer paperWidth
    ) {
        public static PrintJobMessage fromEntity(PrintJob job) {
            return new PrintJobMessage(
                    job.getId(),
                    job.getPrinter().getId(),
                    job.getPrinter().getPrinterName(),
                    job.getPrinter().getIpAddress(),
                    job.getPrinter().getPort() != null ? job.getPrinter().getPort() : 9100,
                    job.getPrinter().getConnectionType(),
                    job.getJobType().name(),
                    job.getOrderId(),
                    job.getOrderNumber(),
                    job.getStationName(),
                    job.getPrintData(),
                    job.getPrinter().getPaperWidth()
            );
        }
    }
}
