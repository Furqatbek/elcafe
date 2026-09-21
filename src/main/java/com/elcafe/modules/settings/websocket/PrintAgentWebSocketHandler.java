package com.elcafe.modules.settings.websocket;

import com.elcafe.modules.settings.entity.PrintJob;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrintAgentWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;
    private final PrintJobRepository printJobRepository;
    private final ObjectMapper objectMapper;

    // Track connected agents: agentId -> restaurantId
    private final Map<String, Long> connectedAgents = new ConcurrentHashMap<>();

    /**
     * Register a print agent connection
     */
    public void registerAgent(String agentId, Long restaurantId) {
        connectedAgents.put(agentId, restaurantId);
        log.info("Print agent registered: {} for restaurant {}", agentId, restaurantId);

        // Send any pending jobs immediately
        sendPendingJobs(agentId, restaurantId);
    }

    /**
     * Unregister a print agent connection
     */
    public void unregisterAgent(String agentId) {
        Long restaurantId = connectedAgents.remove(agentId);
        if (restaurantId != null) {
            log.info("Print agent unregistered: {} (restaurant {})", agentId, restaurantId);
        }
    }

    /**
     * Check if any agent is connected for a restaurant
     */
    public boolean hasConnectedAgent(Long restaurantId) {
        return connectedAgents.values().stream().anyMatch(id -> id.equals(restaurantId));
    }

    /**
     * Get agent ID for a restaurant (returns first connected agent)
     */
    public String getAgentForRestaurant(Long restaurantId) {
        return connectedAgents.entrySet().stream()
                .filter(e -> e.getValue().equals(restaurantId))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
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
     * Send pending jobs to a specific agent
     */
    public void sendPendingJobs(String agentId, Long restaurantId) {
        List<PrintJob> pendingJobs = printJobRepository.findPendingJobsByRestaurant(restaurantId);

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
                .filter(id -> id.equals(restaurantId))
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
