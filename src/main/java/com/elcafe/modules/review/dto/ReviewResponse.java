package com.elcafe.modules.review.dto;

import com.elcafe.modules.review.entity.Review;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private Long orderId;
    private String orderNumber;
    private String customerName;
    private Long restaurantId;
    private Integer rating;
    private String comment;
    private String status;
    private boolean lowRating;
    private String reply;
    private LocalDateTime repliedAt;
    private String repliedBy;
    private LocalDateTime createdAt;

    public static ReviewResponse fromEntity(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .orderId(review.getOrderId())
                .orderNumber(review.getOrderNumber())
                .customerName(review.getCustomerName())
                .restaurantId(review.getRestaurant().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .status(review.getStatus().name())
                .lowRating(review.isLowRating())
                .reply(review.getReply())
                .repliedAt(review.getRepliedAt())
                .repliedBy(review.getRepliedBy())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
