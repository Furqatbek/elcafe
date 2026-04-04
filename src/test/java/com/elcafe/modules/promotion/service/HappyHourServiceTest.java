package com.elcafe.modules.promotion.service;

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
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HappyHourServiceTest {

    @Mock private HappyHourRepository happyHourRepository;
    @Mock private HappyHourScheduleRepository scheduleRepository;
    @Mock private HappyHourProductRepository productRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private ProductRepository menuProductRepository;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private HappyHourService happyHourService;

    private Restaurant restaurant;
    private HappyHour happyHour;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        happyHour = HappyHour.builder()
                .id(1L).restaurant(restaurant).name("Evening Special")
                .description("20% off drinks").discountPercent(new BigDecimal("20"))
                .active(true).priority(1).build();
        happyHour.setSchedules(new HashSet<>());
        happyHour.setProducts(new HashSet<>());
    }

    @Test @DisplayName("getByRestaurant — returns page")
    void getByRestaurant_returnsPage() {
        when(happyHourRepository.findByRestaurantId(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(happyHour), PageRequest.of(0, 20), 1));
        var result = happyHourService.getHappyHoursByRestaurant(1L, PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getById — found")
    void getById_found() {
        when(happyHourRepository.findByIdWithDetails(1L)).thenReturn(happyHour);
        HappyHourResponse result = happyHourService.getHappyHour(1L);
        assertThat(result.getName()).isEqualTo("Evening Special");
    }

    @Test @DisplayName("getById — not found throws")
    void getById_notFound_throws() {
        when(happyHourRepository.findByIdWithDetails(99L)).thenReturn(null);
        assertThatThrownBy(() -> happyHourService.getHappyHour(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test @DisplayName("create — success with schedules")
    void create_success() {
        HappyHourRequest req = new HappyHourRequest();
        req.setName("New HH"); req.setDiscountPercent(new BigDecimal("15")); req.setActive(true);
        HappyHourRequest.ScheduleRequest sched = new HappyHourRequest.ScheduleRequest();
        sched.setDayOfWeek("MON"); sched.setStartTime("17:00"); sched.setEndTime("19:00");
        req.setSchedules(List.of(sched));

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(happyHourRepository.existsByRestaurantIdAndNameIgnoreCase(1L, "New HH")).thenReturn(false);
        when(happyHourRepository.save(any())).thenAnswer(i -> { HappyHour h = i.getArgument(0); h.setId(2L); return h; });

        HappyHourResponse result = happyHourService.createHappyHour(1L, req);
        assertThat(result.getName()).isEqualTo("New HH");
    }

    @Test @DisplayName("update — success")
    void update_success() {
        HappyHourRequest req = new HappyHourRequest();
        req.setName("Updated HH"); req.setDiscountPercent(new BigDecimal("25"));
        when(happyHourRepository.findByIdWithDetails(1L)).thenReturn(happyHour);
        when(happyHourRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(1L, "Updated HH", 1L)).thenReturn(false);
        when(happyHourRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        HappyHourResponse result = happyHourService.updateHappyHour(1L, req);
        assertThat(result.getName()).isEqualTo("Updated HH");
    }

    @Test @DisplayName("delete — success")
    void delete_success() {
        when(happyHourRepository.existsById(1L)).thenReturn(true);
        happyHourService.deleteHappyHour(1L);
        verify(happyHourRepository).deleteById(1L);
    }

    @Test @DisplayName("toggle — flips active")
    void toggle_flipsActive() {
        when(happyHourRepository.findById(1L)).thenReturn(Optional.of(happyHour));
        when(happyHourRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        HappyHourResponse result = happyHourService.toggleHappyHour(1L);
        assertThat(result.getActive()).isFalse();
    }

    @Test @DisplayName("isActive — true when schedule matches current time")
    void isActive_trueWhenScheduleMatches() {
        LocalDateTime now = LocalDateTime.now();
        String today = switch (now.getDayOfWeek()) {
            case MONDAY -> "MON"; case TUESDAY -> "TUE"; case WEDNESDAY -> "WED";
            case THURSDAY -> "THU"; case FRIDAY -> "FRI"; case SATURDAY -> "SAT"; case SUNDAY -> "SUN";
        };
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .dayOfWeek(today).startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59)).build();
        happyHour.setSchedules(new HashSet<>(Set.of(schedule)));

        when(happyHourRepository.findActiveByRestaurantAndDay(1L, today)).thenReturn(List.of(happyHour));
        assertThat(happyHourService.isHappyHourActive(1L, now)).isTrue();
    }

    @Test @DisplayName("isActive — false outside schedule")
    void isActive_falseOutsideSchedule() {
        when(happyHourRepository.findActiveByRestaurantAndDay(eq(1L), anyString())).thenReturn(List.of());
        assertThat(happyHourService.isHappyHourActive(1L, LocalDateTime.now())).isFalse();
    }

    @Test @DisplayName("getActiveHappyHour — returns highest priority")
    void getActiveHappyHour_returnsDetails() {
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .dayOfWeek(switch (LocalDateTime.now().getDayOfWeek()) {
                    case MONDAY -> "MON"; case TUESDAY -> "TUE"; case WEDNESDAY -> "WED";
                    case THURSDAY -> "THU"; case FRIDAY -> "FRI"; case SATURDAY -> "SAT"; case SUNDAY -> "SUN";
                }).startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59)).build();
        happyHour.setSchedules(new HashSet<>(Set.of(schedule)));

        when(happyHourRepository.findActiveWithSchedulesAndProducts(1L)).thenReturn(List.of(happyHour));
        Optional<ActiveHappyHourResponse> result = happyHourService.getActiveHappyHour(1L);
        assertThat(result).isPresent();
        assertThat(result.get().getDiscountPercent()).isEqualByComparingTo("20");
    }

    @Test @DisplayName("getActiveHappyHour — none active returns empty")
    void getActiveHappyHour_noneActive() {
        when(happyHourRepository.findActiveWithSchedulesAndProducts(1L)).thenReturn(List.of());
        Optional<ActiveHappyHourResponse> result = happyHourService.getActiveHappyHour(1L);
        assertThat(result).isEmpty();
    }

    @Test @DisplayName("calculateDiscount — applies percentage to eligible items")
    void calculateDiscount_appliesPercentage() {
        // Setup active happy hour with appliesToAll=true
        HappyHourSchedule schedule = HappyHourSchedule.builder()
                .dayOfWeek(switch (LocalDateTime.now().getDayOfWeek()) {
                    case MONDAY -> "MON"; case TUESDAY -> "TUE"; case WEDNESDAY -> "WED";
                    case THURSDAY -> "THU"; case FRIDAY -> "FRI"; case SATURDAY -> "SAT"; case SUNDAY -> "SUN";
                }).startTime(LocalTime.of(0, 0)).endTime(LocalTime.of(23, 59)).build();
        happyHour.setSchedules(new HashSet<>(Set.of(schedule)));
        happyHour.setProducts(new HashSet<>()); // empty = applies to all

        when(happyHourRepository.findActiveWithSchedulesAndProducts(1L)).thenReturn(List.of(happyHour));

        Order order = new Order();
        order.setRestaurant(restaurant);
        OrderItem item = OrderItem.builder().productId(1L).totalPrice(new BigDecimal("100000")).build();
        order.setItems(new ArrayList<>(List.of(item)));

        BigDecimal discount = happyHourService.calculateHappyHourDiscount(order);
        assertThat(discount).isEqualByComparingTo("20000"); // 20% of 100000
    }
}
