package com.elcafe.modules.promotion.dto;

import com.elcafe.modules.promotion.entity.HappyHour;
import com.elcafe.modules.promotion.entity.HappyHourProduct;
import com.elcafe.modules.promotion.entity.HappyHourSchedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HappyHourResponse {

    private Long id;
    private Long restaurantId;
    private String name;
    private String description;
    private BigDecimal discountPercent;
    private Boolean active;
    private Integer priority;
    private Boolean currentlyActive;
    private List<ScheduleResponse> schedules;
    private List<ProductTargetResponse> productTargets;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleResponse {
        private Long id;
        private String dayOfWeek;
        private String startTime;
        private String endTime;

        public static ScheduleResponse from(HappyHourSchedule schedule) {
            return ScheduleResponse.builder()
                    .id(schedule.getId())
                    .dayOfWeek(schedule.getDayOfWeek())
                    .startTime(formatTime(schedule.getStartTime()))
                    .endTime(formatTime(schedule.getEndTime()))
                    .build();
        }

        private static String formatTime(LocalTime time) {
            return time != null ? String.format("%02d:%02d", time.getHour(), time.getMinute()) : null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductTargetResponse {
        private Long id;
        private Long productId;
        private String productName;
        private Long categoryId;
        private String categoryName;

        public static ProductTargetResponse from(HappyHourProduct product) {
            return ProductTargetResponse.builder()
                    .id(product.getId())
                    .productId(product.getProduct() != null ? product.getProduct().getId() : null)
                    .productName(product.getProduct() != null ? product.getProduct().getName() : null)
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .build();
        }
    }

    public static HappyHourResponse from(HappyHour happyHour) {
        return HappyHourResponse.builder()
                .id(happyHour.getId())
                .restaurantId(happyHour.getRestaurant().getId())
                .name(happyHour.getName())
                .description(happyHour.getDescription())
                .discountPercent(happyHour.getDiscountPercent())
                .active(happyHour.getActive())
                .priority(happyHour.getPriority())
                .currentlyActive(happyHour.isCurrentlyActive())
                .schedules(happyHour.getSchedules() != null ?
                        happyHour.getSchedules().stream()
                                .map(ScheduleResponse::from)
                                .collect(Collectors.toList()) : List.of())
                .productTargets(happyHour.getProducts() != null ?
                        happyHour.getProducts().stream()
                                .map(ProductTargetResponse::from)
                                .collect(Collectors.toList()) : List.of())
                .createdAt(happyHour.getCreatedAt())
                .updatedAt(happyHour.getUpdatedAt())
                .build();
    }
}
