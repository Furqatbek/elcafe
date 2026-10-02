package com.elcafe.modules.settings.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * Whether this venue's kitchen tickets are actually going to come out of a printer.
 *
 * <p>The backend runs in a datacentre and the printer is in a kitchen, so nothing in the cloud can
 * see a printer directly. What it can see is whether the agent that owns the printer is still talking
 * to us, and whether tickets are accumulating unprinted. Those two together are the honest answer,
 * and neither alone is: an agent can be connected to a printer that is out of paper, and a venue with
 * no orders yet has an empty queue whether or not anything works.
 */
@Data
@Builder
public class PrintAgentStatusResponse {

    public enum State {
        /** An agent is connected and heard from recently. Tickets are going somewhere. */
        ONLINE,
        /** Connected, but tickets are piling up unprinted — paper, power, a jammed head. */
        BACKLOG,
        /** Known to us but silent past the staleness window. Treat as not printing. */
        STALE,
        /** No agent has connected. Either never installed, or switched off. */
        OFFLINE
    }

    private State state;

    private String agentId;
    private OffsetDateTime connectedAt;
    private OffsetDateTime lastSeenAt;

    /** Null when no agent is known. Seconds, so the UI can say "2 minutes ago" in its own words. */
    private Long secondsSinceLastSeen;

    /** Tickets waiting to be printed, including ones queued while nothing was connected. */
    private long queuedJobs;

    /** Tickets that exhausted their retries and need a person. */
    private long deadLetterJobs;

    /** When the oldest unprinted ticket was created, and how long it has waited. */
    private OffsetDateTime oldestQueuedAt;
    private Long oldestQueuedMinutes;

    /** The threshold the BACKLOG state uses, so the screen can explain itself without hardcoding it. */
    private long backlogAfterMinutes;

    /**
     * When this venue's agent credential runs out, as last seen.
     *
     * <p>An agent token lasts a year, which makes its expiry the one failure that arrives with nothing
     * having changed: the machine is on, the agent is running, and it simply cannot connect. Carried
     * here so a venue can renew in advance, and so that when it does happen the screen can name it
     * rather than sending somebody to check a plug.
     *
     * <p>Null when no agent has connected since the last restart — the fact lives in a token somebody
     * else is holding, not in our database.
     */
    private OffsetDateTime tokenExpiresAt;

    /** Negative once it has run out, so the UI can treat "expiring" and "expired" as one number. */
    private Long tokenExpiresInDays;

    /** True when the credential has run out — the likely reason an agent is absent without having moved. */
    private boolean tokenExpired;
}
