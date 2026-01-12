package com.elcafe.modules.promotion.service;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.promotion.dto.ActiveHappyHourResponse;
import com.elcafe.modules.promotion.dto.HappyHourRequest;
import com.elcafe.modules.promotion.dto.HappyHourResponse;
import com.elcafe.modules.promotion.entity.HappyHour;
import com.elcafe.modules.promotion.entity.HappyHourProduct;
import com.elcafe.modules.promotion.entity.HappyHourSchedule;
import com.elcafe.modules.promotion.repository.HappyHourProductRepository;
import com.elcafe.modules.promotion.repository.HappyHourRepository;
import com.elcafe.modules.promotion.repository.HappyHourScheduleRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HappyHourService {

    private final HappyHourRepository happyHourRepository;
    private final HappyHourScheduleRepository scheduleRepository;
    private final HappyHourProductRepository productRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository menuProductRepository;
    private final CategoryRepository categoryRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Get all happy hours for a restaurant
     */
    @Transactional(readOnly = true)
    public Page<HappyHourResponse> getHappyHoursByRestaurant(Long restaurantId, Pageable pageable) {
        return happyHourRepository.findByRestaurantId(restaurantId, pageable)
                .map(HappyHourResponse::from);
    }

    /**
     * Get a single happy hour by ID
     */
    @Transactional(readOnly = true)
    public HappyHourResponse getHappyHour(Long id) {
        HappyHour happyHour = happyHourRepository.findByIdWithDetails(id);
        if (happyHour == null) {
            throw new EntityNotFoundException("Happy hour not found with id: " + id);
        }
        return HappyHourResponse.from(happyHour);
    }

    /**
     * Create a new happy hour
     */
    @Transactional
    public HappyHourResponse createHappyHour(Long restaurantId, HappyHourRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new EntityNotFoundException("Restaurant not found"));

        // Check for duplicate name
        if (happyHourRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, request.getName())) {
            throw new IllegalArgumentException("Happy hour with this name already exists");
        }

        HappyHour happyHour = HappyHour.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .discountPercent(request.getDiscountPercent())
                .active(request.getActive() != null ? request.getActive() : true)
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .build();

        // Add schedules
        if (request.getSchedules() != null) {
            for (HappyHourRequest.ScheduleRequest scheduleReq : request.getSchedules()) {
                HappyHourSchedule schedule = HappyHourSchedule.builder()
                        .dayOfWeek(scheduleReq.getDayOfWeek())
                        .startTime(LocalTime.parse(scheduleReq.getStartTime(), TIME_FORMATTER))
                        .endTime(LocalTime.parse(scheduleReq.getEndTime(), TIME_FORMATTER))
                        .build();
                happyHour.addSchedule(schedule);
            }
        }

        // Add product targets
        if (request.getProductTargets() != null) {
            for (HappyHourRequest.ProductTargetRequest targetReq : request.getProductTargets()) {
                HappyHourProduct target = new HappyHourProduct();

                if (targetReq.getProductId() != null) {
                    Product product = menuProductRepository.findById(targetReq.getProductId())
                            .orElseThrow(() -> new EntityNotFoundException("Product not found: " + targetReq.getProductId()));
                    target.setProduct(product);
                }

                if (targetReq.getCategoryId() != null) {
                    Category category = categoryRepository.findById(targetReq.getCategoryId())
                            .orElseThrow(() -> new EntityNotFoundException("Category not found: " + targetReq.getCategoryId()));
                    target.setCategory(category);
                }

                happyHour.addProduct(target);
            }
        }

        HappyHour saved = happyHourRepository.save(happyHour);
        log.info("Created happy hour: {} for restaurant: {}", saved.getId(), restaurantId);

        return HappyHourResponse.from(saved);
    }

    /**
     * Update an existing happy hour
     */
    @Transactional
    public HappyHourResponse updateHappyHour(Long id, HappyHourRequest request) {
        HappyHour happyHour = happyHourRepository.findByIdWithDetails(id);
        if (happyHour == null) {
            throw new EntityNotFoundException("Happy hour not found");
        }

        // Check for duplicate name (excluding current)
        if (happyHourRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(
                happyHour.getRestaurant().getId(), request.getName(), id)) {
            throw new IllegalArgumentException("Happy hour with this name already exists");
        }

        happyHour.setName(request.getName());
        happyHour.setDescription(request.getDescription());
        happyHour.setDiscountPercent(request.getDiscountPercent());
        happyHour.setActive(request.getActive() != null ? request.getActive() : happyHour.getActive());
        happyHour.setPriority(request.getPriority() != null ? request.getPriority() : happyHour.getPriority());

        // Update schedules - clear and recreate
        happyHour.clearSchedules();
        if (request.getSchedules() != null) {
            for (HappyHourRequest.ScheduleRequest scheduleReq : request.getSchedules()) {
                HappyHourSchedule schedule = HappyHourSchedule.builder()
                        .dayOfWeek(scheduleReq.getDayOfWeek())
                        .startTime(LocalTime.parse(scheduleReq.getStartTime(), TIME_FORMATTER))
                        .endTime(LocalTime.parse(scheduleReq.getEndTime(), TIME_FORMATTER))
                        .build();
                happyHour.addSchedule(schedule);
            }
        }

        // Update product targets - clear and recreate
        happyHour.clearProducts();
        if (request.getProductTargets() != null) {
            for (HappyHourRequest.ProductTargetRequest targetReq : request.getProductTargets()) {
                HappyHourProduct target = new HappyHourProduct();

                if (targetReq.getProductId() != null) {
                    Product product = menuProductRepository.findById(targetReq.getProductId())
                            .orElseThrow(() -> new EntityNotFoundException("Product not found"));
                    target.setProduct(product);
                }

                if (targetReq.getCategoryId() != null) {
                    Category category = categoryRepository.findById(targetReq.getCategoryId())
                            .orElseThrow(() -> new EntityNotFoundException("Category not found"));
                    target.setCategory(category);
                }

                happyHour.addProduct(target);
            }
        }

        HappyHour saved = happyHourRepository.save(happyHour);
        log.info("Updated happy hour: {}", saved.getId());

        return HappyHourResponse.from(saved);
    }

    /**
     * Delete a happy hour
     */
    @Transactional
    public void deleteHappyHour(Long id) {
        if (!happyHourRepository.existsById(id)) {
            throw new EntityNotFoundException("Happy hour not found");
        }
        happyHourRepository.deleteById(id);
        log.info("Deleted happy hour: {}", id);
    }

    /**
     * Toggle happy hour active status
     */
    @Transactional
    public HappyHourResponse toggleHappyHour(Long id) {
        HappyHour happyHour = happyHourRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Happy hour not found"));

        happyHour.setActive(!happyHour.getActive());
        HappyHour saved = happyHourRepository.save(happyHour);

        log.info("Toggled happy hour {} to active: {}", id, saved.getActive());
        return HappyHourResponse.from(saved);
    }

    /**
     * Check if any happy hour is currently active for a restaurant
     */
    @Transactional(readOnly = true)
    public boolean isHappyHourActive(Long restaurantId) {
        return getActiveHappyHour(restaurantId).isPresent();
    }

    /**
     * Check if any happy hour is active at a specific datetime for a restaurant
     */
    @Transactional(readOnly = true)
    public boolean isHappyHourActive(Long restaurantId, LocalDateTime dateTime) {
        String dayOfWeek = toDayString(dateTime.getDayOfWeek());
        List<HappyHour> candidates = happyHourRepository.findActiveByRestaurantAndDay(restaurantId, dayOfWeek);

        return candidates.stream().anyMatch(h -> h.isActiveAt(dateTime));
    }

    /**
     * Get the currently active happy hour for a restaurant (highest priority)
     */
    @Transactional(readOnly = true)
    public Optional<ActiveHappyHourResponse> getActiveHappyHour(Long restaurantId) {
        List<HappyHour> activeHappyHours = happyHourRepository.findActiveWithSchedulesAndProducts(restaurantId);

        return activeHappyHours.stream()
                .filter(HappyHour::isCurrentlyActive)
                .max(Comparator.comparingInt(HappyHour::getPriority))
                .map(this::toActiveResponse);
    }

    /**
     * Get active happy hour at a specific datetime
     */
    @Transactional(readOnly = true)
    public Optional<ActiveHappyHourResponse> getActiveHappyHour(Long restaurantId, LocalDateTime dateTime) {
        List<HappyHour> activeHappyHours = happyHourRepository.findActiveWithSchedulesAndProducts(restaurantId);

        return activeHappyHours.stream()
                .filter(h -> h.isActiveAt(dateTime))
                .max(Comparator.comparingInt(HappyHour::getPriority))
                .map(this::toActiveResponse);
    }

    /**
     * Calculate happy hour discount for an order
     * Returns the discount amount to be applied
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateHappyHourDiscount(Order order) {
        if (order.getRestaurant() == null) {
            return BigDecimal.ZERO;
        }

        Optional<ActiveHappyHourResponse> activeHappyHour = getActiveHappyHour(order.getRestaurant().getId());
        if (activeHappyHour.isEmpty()) {
            return BigDecimal.ZERO;
        }

        ActiveHappyHourResponse happyHour = activeHappyHour.get();
        BigDecimal applicableAmount = BigDecimal.ZERO;

        for (OrderItem item : order.getItems()) {
            if (isProductEligible(item, happyHour)) {
                applicableAmount = applicableAmount.add(item.getTotalPrice());
            }
        }

        // Calculate discount
        BigDecimal discount = applicableAmount
                .multiply(happyHour.getDiscountPercent())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        log.debug("Happy hour discount calculated: {} for order subtotal: {}", discount, applicableAmount);
        return discount;
    }

    /**
     * Check if a product is eligible for happy hour discount
     */
    private boolean isProductEligible(OrderItem item, ActiveHappyHourResponse happyHour) {
        // If applies to all, everything is eligible
        if (Boolean.TRUE.equals(happyHour.getAppliesToAll())) {
            return true;
        }

        Long productId = item.getProductId();

        // Check specific products
        if (happyHour.getApplicableProductIds() != null &&
            happyHour.getApplicableProductIds().contains(productId)) {
            return true;
        }

        // Check categories - need to look up the product to get its category
        if (happyHour.getApplicableCategoryIds() != null && !happyHour.getApplicableCategoryIds().isEmpty()) {
            Optional<Product> product = menuProductRepository.findById(productId);
            if (product.isPresent() && product.get().getCategory() != null) {
                Long categoryId = product.get().getCategory().getId();
                if (happyHour.getApplicableCategoryIds().contains(categoryId)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Convert HappyHour to ActiveHappyHourResponse
     */
    private ActiveHappyHourResponse toActiveResponse(HappyHour happyHour) {
        LocalTime now = LocalTime.now();
        DayOfWeek today = LocalDateTime.now().getDayOfWeek();

        // Find current schedule's end time
        String endsAt = happyHour.getSchedules().stream()
                .filter(s -> s.getDayOfWeek().equals(toDayString(today)))
                .filter(s -> !now.isBefore(s.getStartTime()) && !now.isAfter(s.getEndTime()))
                .findFirst()
                .map(s -> s.getEndTime().format(TIME_FORMATTER))
                .orElse(null);

        // Get applicable product/category IDs
        List<Long> productIds = happyHour.getProducts().stream()
                .filter(p -> p.getProduct() != null)
                .map(p -> p.getProduct().getId())
                .collect(Collectors.toList());

        List<Long> categoryIds = happyHour.getProducts().stream()
                .filter(p -> p.getCategory() != null)
                .map(p -> p.getCategory().getId())
                .collect(Collectors.toList());

        boolean appliesToAll = happyHour.getProducts().isEmpty();

        return ActiveHappyHourResponse.builder()
                .id(happyHour.getId())
                .name(happyHour.getName())
                .description(happyHour.getDescription())
                .discountPercent(happyHour.getDiscountPercent())
                .endsAt(endsAt)
                .applicableProductIds(productIds.isEmpty() ? null : productIds)
                .applicableCategoryIds(categoryIds.isEmpty() ? null : categoryIds)
                .appliesToAll(appliesToAll)
                .build();
    }

    private String toDayString(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }
}
