package com.elcafe.modules.review.controller;

import com.elcafe.modules.review.dto.ReviewResponse;
import com.elcafe.modules.review.entity.Review;
import com.elcafe.modules.review.service.ReviewService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getReviews(
            @PathVariable Long restaurantId) {
        List<ReviewResponse> reviews = reviewService.getAllRestaurantReviews(restaurantId).stream()
                .map(ReviewResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved", reviews));
    }

    @GetMapping("/restaurant/{restaurantId}/summary")
    public ResponseEntity<ApiResponse<ReviewService.ReviewSummary>> getSummary(
            @PathVariable Long restaurantId) {
        ReviewService.ReviewSummary summary = reviewService.getReviewSummary(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Summary retrieved", summary));
    }

    @PostMapping("/{id}/reply")
    public ResponseEntity<ApiResponse<ReviewResponse>> replyToReview(
            @PathVariable Long id,
            @RequestBody ReplyRequest request) {
        log.info("Replying to review {}", id);
        Review review = reviewService.replyToReview(id, request.reply, request.repliedBy);
        return ResponseEntity.ok(ApiResponse.success("Reply added", ReviewResponse.fromEntity(review)));
    }

    @PostMapping("/{id}/hide")
    public ResponseEntity<ApiResponse<ReviewResponse>> hideReview(@PathVariable Long id) {
        log.info("Hiding review {}", id);
        Review review = reviewService.hideReview(id);
        return ResponseEntity.ok(ApiResponse.success("Review hidden", ReviewResponse.fromEntity(review)));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ReviewResponse>> publishReview(@PathVariable Long id) {
        log.info("Publishing review {}", id);
        Review review = reviewService.publishReview(id);
        return ResponseEntity.ok(ApiResponse.success("Review published", ReviewResponse.fromEntity(review)));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ReplyRequest {
        private String reply;
        private String repliedBy;
    }
}
