package com.elcafe.modules.settings.websocket;

import com.elcafe.modules.settings.service.PrintJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The print-agent SEND handlers must not let a session act on another tenant's jobs (audit #21). The
 * session's tenant is bound at CONNECT by StompAuthChannelInterceptor ({@code ws.restaurantId}); these
 * verify the ownership guard, including the no-op path for an unauthenticated session (auth OFF).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PrintAgentWebSocketControllerTest {

    @Mock private PrintAgentWebSocketHandler printAgentHandler;
    @Mock private PrintJobService printJobService;
    @InjectMocks private PrintAgentWebSocketController controller;

    private SimpMessageHeaderAccessor sessionBoundTo(Long tenant) {
        SimpMessageHeaderAccessor acc = SimpMessageHeaderAccessor.create();
        Map<String, Object> attrs = new HashMap<>();
        if (tenant != null) {
            attrs.put("ws.restaurantId", tenant);
        }
        acc.setSessionAttributes(attrs);
        return acc;
    }

    @Test
    @DisplayName("get-jobs for OWN tenant is served")
    void getJobs_ownTenant_ok() {
        var msg = new PrintAgentWebSocketController.GetJobsMessage("agent-1", 5L);
        assertThatCode(() -> controller.handleGetJobs(msg, sessionBoundTo(5L))).doesNotThrowAnyException();
        verify(printAgentHandler).sendPendingJobs("agent-1", 5L);
    }

    @Test
    @DisplayName("get-jobs for ANOTHER tenant is rejected")
    void getJobs_crossTenant_rejected() {
        var msg = new PrintAgentWebSocketController.GetJobsMessage("agent-1", 9L);
        assertThatThrownBy(() -> controller.handleGetJobs(msg, sessionBoundTo(5L)))
                .isInstanceOf(AccessDeniedException.class);
        verify(printAgentHandler, never()).sendPendingJobs(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("unauthenticated session (auth OFF, no bound tenant) is not blocked — rollout compat")
    void getJobs_noBoundTenant_allowed() {
        var msg = new PrintAgentWebSocketController.GetJobsMessage("agent-1", 9L);
        assertThatCode(() -> controller.handleGetJobs(msg, sessionBoundTo(null))).doesNotThrowAnyException();
        verify(printAgentHandler).sendPendingJobs("agent-1", 9L);
    }

    @Test
    @DisplayName("job-completed on ANOTHER tenant's job is rejected (no cross-tenant sabotage)")
    void jobCompleted_crossTenant_rejected() {
        when(printJobService.restaurantIdOfJob(42L)).thenReturn(9L); // job belongs to tenant 9
        var msg = new PrintAgentWebSocketController.JobCompletedMessage(42L, "agent-1");
        assertThatThrownBy(() -> controller.handleJobCompleted(msg, sessionBoundTo(5L)))
                .isInstanceOf(AccessDeniedException.class);
        verify(printJobService, never()).markJobCompleted(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("job-completed on OWN tenant's job is applied")
    void jobCompleted_ownTenant_ok() {
        when(printJobService.restaurantIdOfJob(42L)).thenReturn(5L);
        var msg = new PrintAgentWebSocketController.JobCompletedMessage(42L, "agent-1");
        assertThatCode(() -> controller.handleJobCompleted(msg, sessionBoundTo(5L))).doesNotThrowAnyException();
        verify(printJobService).markJobCompleted(42L);
    }
}
