package com.elcafe.modules.instagram.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "instagram_subscriber_addresses")
// V163: carries its own restaurant_id (denormalized from the parent subscriber) so the §3.4
// restaurantFilter scopes it directly rather than relying only on reaching it through a scoped
// subscriber.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramSubscriberAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — always mirrors {@code subscriber.restaurantId}. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscriber_id", nullable = false)
    private InstagramSubscriber subscriber;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
}
