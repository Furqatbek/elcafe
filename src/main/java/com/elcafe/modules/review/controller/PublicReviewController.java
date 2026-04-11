package com.elcafe.modules.review.controller;

import com.elcafe.modules.review.dto.ReviewResponse;
import com.elcafe.modules.review.dto.SubmitReviewRequest;
import com.elcafe.modules.review.entity.Review;
import com.elcafe.modules.review.service.ReviewService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/public/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @Valid @RequestBody SubmitReviewRequest request) {
        log.info("Review submitted: rating={} restaurant={} order={}",
                request.getRating(), request.getRestaurantId(), request.getOrderNumber());

        Review review = reviewService.submitReview(request);
        ReviewResponse response = ReviewResponse.fromEntity(review);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully", response));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<ReviewResponse>>> getRestaurantReviews(
            @PathVariable Long restaurantId) {
        List<ReviewResponse> reviews = reviewService.getRestaurantReviews(restaurantId).stream()
                .map(ReviewResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Reviews retrieved", reviews));
    }

    @GetMapping("/restaurant/{restaurantId}/summary")
    public ResponseEntity<ApiResponse<ReviewService.ReviewSummary>> getReviewSummary(
            @PathVariable Long restaurantId) {
        ReviewService.ReviewSummary summary = reviewService.getReviewSummary(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Review summary retrieved", summary));
    }
}
