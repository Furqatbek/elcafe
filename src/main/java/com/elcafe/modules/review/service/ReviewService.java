package com.elcafe.modules.review.service;

import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.review.dto.SubmitReviewRequest;
import com.elcafe.modules.review.entity.Review;
import com.elcafe.modules.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final RestaurantRepository restaurantRepository;
    // Lazy because OwnerNotificationService transitively pulls in many
    // financial services that aren't ready at startup before reviews are.
    @org.springframework.context.annotation.Lazy
    private final OwnerNotificationService ownerNotificationService;

    @Transactional
    public Review submitReview(SubmitReviewRequest request) {
        // Check duplicate if orderId provided
        if (request.getOrderId() != null) {
            reviewRepository.findByOrderId(request.getOrderId()).ifPresent(existing -> {
                throw new IllegalStateException("This order has already been reviewed");
            });
        }

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Review review = Review.builder()
                .orderId(request.getOrderId())
                .orderNumber(request.getOrderNumber())
                .customerName(request.getCustomerName())
                .restaurant(restaurant)
                .rating(request.getRating())
                .comment(request.getComment())
                .status(Review.Status.PUBLISHED)
                .build();

        review = reviewRepository.save(review);

        // Recalculate restaurant average rating
        recalculateRating(request.getRestaurantId());

        log.info("Review submitted: rating={} for restaurant={} order={}",
                request.getRating(), request.getRestaurantId(), request.getOrderNumber());

        // Fan out to the owner-bot subscribers. The notification path is
        // @Async on the other side; failures inside it must not roll back
        // the review submission so we swallow + log here too.
        try {
            ownerNotificationService.notifyCustomerReview(
                    request.getRestaurantId(),
                    request.getCustomerName(),
                    request.getRating(),
                    request.getComment(),
                    review.getId());
        } catch (Exception e) {
            log.error("Failed to dispatch review notification for review {}: {}",
                    review.getId(), e.getMessage());
        }

        return review;
    }

    @Transactional(readOnly = true)
    public List<Review> getRestaurantReviews(Long restaurantId) {
        return reviewRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(
                restaurantId, Review.Status.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<Review> getAllRestaurantReviews(Long restaurantId) {
        return reviewRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    @Transactional(readOnly = true)
    public ReviewSummary getReviewSummary(Long restaurantId) {
        BigDecimal avg = reviewRepository.getAverageRating(restaurantId);
        long total = reviewRepository.countPublishedByRestaurantId(restaurantId);
        List<Object[]> distribution = reviewRepository.getRatingDistribution(restaurantId);

        int[] counts = new int[5]; // index 0=1star, 4=5star
        for (Object[] row : distribution) {
            int star = (Integer) row[0];
            long count = (Long) row[1];
            counts[star - 1] = (int) count;
        }

        return new ReviewSummary(
                avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                total, counts[4], counts[3], counts[2], counts[1], counts[0]);
    }

    @Transactional
    public Review replyToReview(Long reviewId, String reply, String repliedBy) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setReply(reply);
        review.setRepliedAt(LocalDateTime.now());
        review.setRepliedBy(repliedBy);
        return reviewRepository.save(review);
    }

    @Transactional
    public Review hideReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setStatus(Review.Status.HIDDEN);
        return reviewRepository.save(review);
    }

    @Transactional
    public Review publishReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Review not found"));
        review.setStatus(Review.Status.PUBLISHED);
        return reviewRepository.save(review);
    }

    private void recalculateRating(Long restaurantId) {
        BigDecimal avg = reviewRepository.getAverageRating(restaurantId);
        if (avg != null) {
            Restaurant restaurant = restaurantRepository.findById(restaurantId).orElse(null);
            if (restaurant != null) {
                restaurant.setRating(avg.setScale(1, RoundingMode.HALF_UP));
                restaurantRepository.save(restaurant);
            }
        }
    }

    public record ReviewSummary(
            BigDecimal averageRating,
            long totalReviews,
            int fiveStars,
            int fourStars,
            int threeStars,
            int twoStars,
            int oneStars
    ) {}
}
