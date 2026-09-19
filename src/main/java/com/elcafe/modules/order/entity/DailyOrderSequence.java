package com.elcafe.modules.order.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "daily_order_sequences")
public class DailyOrderSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Written once, when the day's first order is numbered, and never again.
     *
     * <p>Rewriting it is how order numbering broke: a date read back a day short and saved again
     * walks into the past, until the lookup for "today" misses, a second row appears and the counter
     * restarts. {@code DailyOrderSequenceService} takes numbers with an UPDATE that names only
     * {@code currentSequence} precisely so this column is never in the statement.
     */
    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false)
    @Builder.Default
    private Integer currentSequence = 0;

    /**
     * Guards entity-level writes. The counter is incremented by a bulk UPDATE, which by design does
     * not bump this — safe only because nothing else updates these rows. Anything that starts doing
     * so needs to reckon with that rather than assume the version is moving.
     */
    @Version
    private Long version;
}
