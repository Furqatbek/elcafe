package com.elcafe.modules.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

/**
 * One thing known about a guest's taste (V183): something they like, dislike, must avoid, or a dietary
 * rule they keep.
 *
 * <p>Structured on purpose. The same information can already be typed into {@code Customer.notes}, but
 * a sentence cannot answer "which guests are vegetarian" or put an allergy in front of a waiter at the
 * moment it matters. Each row is one fact, so it can be filtered, segmented and surfaced.
 *
 * <p>{@link Type#ALLERGY} is separate from {@link Type#DISLIKE} deliberately — they look alike in a
 * list and mean very different things. One is taste, the other is safety.
 */
@Entity
@Table(name = "customer_preference")
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPreference {

    public enum Type {
        LIKE,
        DISLIKE,
        /** Safety-critical. Surfaced more prominently than a dislike, and never silently dropped. */
        ALLERGY,
        /** A standing rule rather than a preference — vegetarian, halal, no alcohol. */
        DIETARY
    }

    public enum Source {
        /** A person recorded this — the guest said so, or staff were told. Trustworthy. */
        MANUAL,
        /** Inferred, e.g. from order history. A guess: never present it as something the guest stated. */
        DERIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "preference_type", nullable = false, length = 20)
    private Type preferenceType;

    /**
     * The thing itself — "coriander", "walnuts", "vegetarian".
     *
     * <p>Mapped to {@code preference_value}, not {@code value}: the latter is a reserved word in H2,
     * which the test suite builds its schema on, so a bare {@code value} column passes on Postgres and
     * fails every {@code @DataJpaTest} that touches this table.
     */
    @Column(name = "preference_value", nullable = false, length = 120)
    private String value;

    /** Optional free text for the detail a single word loses ("mild reaction, avoid in sauces"). */
    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Source source = Source.MANUAL;

    /** Who recorded it, when a person did. Null for a DERIVED row, which no one typed. */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
