package com.elcafe.modules.review.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "reviews", indexes = {
        @Index(name = "idx_reviews_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_reviews_rating", columnList = "restaurant_id, rating"),
        @Index(name = "idx_reviews_created", columnList = "restaurant_id, created_at"),
        @Index(name = "idx_reviews_status", columnList = "restaurant_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PUBLISHED;

    @Column(columnDefinition = "TEXT")
    private String reply;

    @Column
    private LocalDateTime repliedAt;

    @Column(length = 100)
    private String repliedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        PUBLISHED,
        HIDDEN
    }

    public boolean isLowRating() {
        return rating != null && rating <= 2;
    }
}
