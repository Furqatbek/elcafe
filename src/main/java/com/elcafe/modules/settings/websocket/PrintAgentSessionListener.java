package com.elcafe.modules.settings.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Removes a print agent when its connection goes away, however it goes away.
 *
 * <p>The agent sends a DISCONNECT frame when it is shut down politely, and that path already worked.
 * The paths that matter in a kitchen do not: the machine is switched off at the wall, the network
 * drops, the process is killed. None of those send anything, so without this the agent stays in the
 * live map for as long as the server runs — and anything asking "is a printer reachable" gets told
 * yes forever.
 *
 * <p>Spring raises {@link SessionDisconnectEvent} for all of them, because it fires on transport
 * close and not only on a client's say-so. Pairing it with the staleness window in
 * {@link PrintAgentWebSocketHandler} covers the remaining case, a half-open socket that never closes
 * at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrintAgentSessionListener {

    private final PrintAgentWebSocketHandler printAgentHandler;

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return;
        }
        // Cheap and unconditional: most disconnects belong to the admin or waiter sockets and match no
        // agent, in which case this does nothing. Matching on the session rather than on a session
        // attribute means it still works when the attributes have already been cleared.
        printAgentHandler.unregisterSession(sessionId);
    }
}
