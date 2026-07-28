package com.elcafe.common.channel;

/**
 * The slice of a per-tenant channel broadcast campaign that {@link AbstractChannelCampaignService}
 * needs to run the shared read / cancel / delete flow without depending on any channel's status enum.
 *
 * <p>The campaign's own lifecycle rules live here, on the entity, rather than as status-enum
 * comparisons scattered through the services: a campaign knows whether it may still be cancelled or
 * deleted. Keeping the rules on the entity is also what lets the shared base stay in {@code common}
 * without reaching into a module's {@code CampaignStatus} enum.
 */
public interface ChannelCampaign {

    /** Whether the campaign may still be cancelled (i.e. it has not already completed). */
    boolean isCancellable();

    /** Move the campaign to the cancelled state. Only called after {@link #isCancellable()} passes. */
    void markCancelled();

    /** Whether the campaign may be deleted (i.e. it is not mid-send). */
    boolean isDeletable();
}
