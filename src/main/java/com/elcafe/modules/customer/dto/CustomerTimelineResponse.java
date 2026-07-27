package com.elcafe.modules.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A window of a guest's conversation history, newest first — the "recent messages, press for more"
 * shape rather than a full dump.
 *
 * <p>Cursored on timestamp rather than an offset page number, deliberately: the stream is merged from
 * several tables that are still being written to, so an offset would skip or repeat messages the moment
 * a new one arrived mid-scroll. A "everything older than this instant" cursor cannot drift that way.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTimelineResponse {

    private List<CustomerTimelineEntry> entries;

    /**
     * Feed back as {@code before} to fetch the next, older window. Null when the history is exhausted.
     */
    private OffsetDateTime nextCursor;

    /** False when this window reached the beginning of the guest's history — hide "load more". */
    private Boolean hasMore;
}
