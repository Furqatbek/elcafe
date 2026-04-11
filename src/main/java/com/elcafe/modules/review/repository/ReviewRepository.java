package com.elcafe.modules.review.repository;

import com.elcafe.modules.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderId(Long orderId);

    List<Review> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, Review.Status status);

    List<Review> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.restaurant.id = :restaurantId AND r.status = 'PUBLISHED'")
    BigDecimal getAverageRating(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.restaurant.id = :restaurantId AND r.status = 'PUBLISHED'")
    long countPublishedByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.restaurant.id = :restaurantId AND r.status = 'PUBLISHED' GROUP BY r.rating ORDER BY r.rating DESC")
    List<Object[]> getRatingDistribution(@Param("restaurantId") Long restaurantId);

    List<Review> findByRestaurantIdAndRatingLessThanEqualAndStatusOrderByCreatedAtDesc(
            Long restaurantId, Integer rating, Review.Status status);
}
