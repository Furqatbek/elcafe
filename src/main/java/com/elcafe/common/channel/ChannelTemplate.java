package com.elcafe.common.channel;

import java.util.Map;

/**
 * The slice of a per-tenant channel message template that {@link AbstractChannelTemplateService} needs
 * in order to run the shared CRUD flow without knowing the concrete entity. A channel's template
 * entity (SMS, Telegram, …) implements this; everything channel-specific — its extra columns, its
 * builder, its response mapping — stays on the entity and its own service.
 */
public interface ChannelTemplate {

    /** Human-facing template name, unique within a restaurant; used for the duplicate-name guard. */
    String getName();

    /** Whether the template is currently usable; drives the toggle action. */
    Boolean getIsActive();

    void setIsActive(Boolean isActive);

    /** Substitute {@code {key}} placeholders with the given values (see {@code MessageTemplateRenderer}). */
    String render(Map<String, String> placeholders);
}
