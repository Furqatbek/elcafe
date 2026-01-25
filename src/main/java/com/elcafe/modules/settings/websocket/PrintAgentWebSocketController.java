package com.elcafe.modules.settings.websocket;

import com.elcafe.modules.settings.service.PrintJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for print agent communication
 * Handles print agent registration, job acknowledgments, and status updates
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PrintAgentWebSocketController {

    private final PrintAgentWebSocketHandler printAgentHandler;
    private final PrintJobService printJobService;

    /**
     * Handle print agent connection/registration
     * Agent sends: { "agentId": "agent-123", "restaurantId": 1 }
     */
    @MessageMapping("/print-agent/connect")
    public void handleAgentConnect(@Payload AgentConnectMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        log.info("Print agent connecting: {} for restaurant {} (session: {})",
                message.agentId(), message.restaurantId(), sessionId);

        // Store agent ID in session attributes for disconnect handling
        headerAccessor.getSessionAttributes().put("agentId", message.agentId());
        headerAccessor.getSessionAttributes().put("restaurantId", message.restaurantId());

        printAgentHandler.registerAgent(message.agentId(), message.restaurantId());
    }

    /**
     * Handle print agent disconnection
     * Agent sends: { "agentId": "agent-123" }
     */
    @MessageMapping("/print-agent/disconnect")
    public void handleAgentDisconnect(@Payload AgentDisconnectMessage message) {
        log.info("Print agent disconnecting: {}", message.agentId());
        printAgentHandler.unregisterAgent(message.agentId());
    }

    /**
     * Handle print job acknowledgment (job received by agent)
     * Agent sends: { "jobId": 123, "agentId": "agent-123" }
     */
    @MessageMapping("/print-agent/job-received")
    public void handleJobReceived(@Payload JobReceivedMessage message) {
        log.info("Print job {} received by agent {}", message.jobId(), message.agentId());
        printJobService.markJobSent(message.jobId(), message.agentId());
    }

    /**
     * Handle print job completion
     * Agent sends: { "jobId": 123, "agentId": "agent-123" }
     */
    @MessageMapping("/print-agent/job-completed")
    public void handleJobCompleted(@Payload JobCompletedMessage message) {
        log.info("Print job {} completed by agent {}", message.jobId(), message.agentId());
        printJobService.markJobCompleted(message.jobId());
    }

    /**
     * Handle print job failure
     * Agent sends: { "jobId": 123, "agentId": "agent-123", "error": "Printer offline" }
     */
    @MessageMapping("/print-agent/job-failed")
    public void handleJobFailed(@Payload JobFailedMessage message) {
        log.warn("Print job {} failed: {}", message.jobId(), message.error());
        printJobService.markJobFailed(message.jobId(), message.error());
    }

    /**
     * Handle request for pending jobs
     * Agent sends: { "agentId": "agent-123", "restaurantId": 1 }
     */
    @MessageMapping("/print-agent/get-jobs")
    public void handleGetJobs(@Payload GetJobsMessage message) {
        log.debug("Agent {} requesting pending jobs for restaurant {}", message.agentId(), message.restaurantId());
        printAgentHandler.sendPendingJobs(message.agentId(), message.restaurantId());
    }

    // Message record classes
    public record AgentConnectMessage(String agentId, Long restaurantId) {}
    public record AgentDisconnectMessage(String agentId) {}
    public record JobReceivedMessage(Long jobId, String agentId) {}
    public record JobCompletedMessage(Long jobId, String agentId) {}
    public record JobFailedMessage(Long jobId, String agentId, String error) {}
    public record GetJobsMessage(String agentId, Long restaurantId) {}
}
