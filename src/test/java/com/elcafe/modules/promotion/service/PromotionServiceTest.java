package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.promotion.repository.*;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromotionServiceTest {

    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionRuleRepository promotionRuleRepository;
    @Mock private PromotionProductRepository promotionProductRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private CouponCodeRepository couponCodeRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private PromotionService promotionService;

    private Restaurant restaurant;
    private Promotion promotion;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant(); restaurant.setId(1L); restaurant.setName("Test");
        promotion = Promotion.builder().id(1L).restaurant(restaurant).name("Summer Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("20")).active(true)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(30))
                .build();
        promotion.setPromotionProducts(new ArrayList<>());

        when(promotionUsageRepository.countByPromotionId(anyLong())).thenReturn(0L);
        when(promotionUsageRepository.sumDiscountByPromotionId(anyLong())).thenReturn(BigDecimal.ZERO);
        when(couponCodeRepository.countByPromotionId(anyLong())).thenReturn(0L);
    }

    @Test @DisplayName("createPromotion — success")
    void createPromotion_success() {
        CreatePromotionRequest req = new CreatePromotionRequest();
        req.setName("New Promo"); req.setPromotionType(PromotionType.PERCENTAGE);
        req.setPromotionScope(PromotionScope.ALL); req.setDiscountValue(new BigDecimal("15"));
        req.setActive(true);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(1L, "New Promo")).thenReturn(false);
        when(promotionRepository.save(any())).thenAnswer(i -> { Promotion p = i.getArgument(0); p.setId(2L); p.setPromotionProducts(new ArrayList<>()); return p; });

        PromotionResponse result = promotionService.createPromotion(1L, req);
        assertThat(result.getName()).isEqualTo("New Promo");
    }

    @Test @DisplayName("createPromotion — duplicate name throws")
    void createPromotion_duplicateCode_throws() {
        CreatePromotionRequest req = new CreatePromotionRequest(); req.setName("Summer Sale");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(1L, "Summer Sale")).thenReturn(true);

        assertThatThrownBy(() -> promotionService.createPromotion(1L, req))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("already exists");
    }

    @Test @DisplayName("updatePromotion — success")
    void updatePromotion_success() {
        UpdatePromotionRequest req = new UpdatePromotionRequest();
        req.setName("Updated Sale"); req.setDiscountValue(new BigDecimal("25"));
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(promotionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PromotionResponse result = promotionService.updatePromotion(1L, req);
        assertThat(result.getName()).isEqualTo("Updated Sale");
    }

    @Test @DisplayName("getPromotion — found")
    void getPromotion_found() {
        when(promotionRepository.findByIdWithDetails(1L)).thenReturn(promotion);
        PromotionResponse result = promotionService.getPromotion(1L);
        assertThat(result.getName()).isEqualTo("Summer Sale");
    }

    @Test @DisplayName("getPromotions — paginated")
    void getPromotions_returnsPage() {
        when(promotionRepository.findByRestaurant_IdOrderByPriorityDescCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(promotion), PageRequest.of(0, 20), 1));
        var result = promotionService.getPromotions(1L, PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getActivePromotions — filters expired")
    void getActivePromotions_filtersExpired() {
        when(promotionRepository.findActivePromotions(eq(1L), any())).thenReturn(List.of(promotion));
        List<PromotionResponse> result = promotionService.getActivePromotions(1L);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("togglePromotion — flips active")
    void togglePromotion_flipsActive() {
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(promotionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PromotionResponse result = promotionService.togglePromotion(1L);
        assertThat(result.getActive()).isFalse();
    }

    @Test @DisplayName("deletePromotion — hard deletes unused")
    void deletePromotion_success() {
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(promotionUsageRepository.countByPromotionId(1L)).thenReturn(0L);
        promotionService.deletePromotion(1L);
        verify(promotionRepository).delete(promotion);
    }
}
