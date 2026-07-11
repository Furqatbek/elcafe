package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import com.elcafe.modules.loyalty.entity.MilestoneRedemption;
import com.elcafe.modules.loyalty.repository.LoyaltyMilestoneRepository;
import com.elcafe.modules.loyalty.repository.MilestoneRedemptionRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Pins the milestone visit counter's per-order replay guard: the same order must never count as two
 * visits, no matter how often its completion event is (re)delivered. This is the third defense layer
 * — behind the publish marker and the loyalty listener's ledger check — and the only one owned by
 * the milestone module itself.
 */
@ExtendWith(MockitoExtension.class)
class MilestoneServiceTest {

    @Mock private LoyaltyMilestoneRepository milestoneRepository;
    @Mock private MilestoneRedemptionRepository redemptionRepository;
    @Mock private com.elcafe.modules.restaurant.repository.RestaurantRepository restaurantRepository;
    @Mock private com.elcafe.modules.menu.repository.ProductRepository productRepository;
    @Mock private com.elcafe.modules.loyalty.mapper.MilestoneMapper milestoneMapper;

    @Mock private LoyaltyMilestone milestone;
    @Mock private Order order;
    @Mock private Order secondOrder;
    @Mock private Customer customer;
    @Mock private Restaurant restaurant;

    @InjectMocks private MilestoneService milestoneService;

    private final AtomicReference<MilestoneRedemption> stored = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        lenient().when(customer.getId()).thenReturn(9L);
        lenient().when(restaurant.getId()).thenReturn(1L);

        lenient().when(order.getId()).thenReturn(5L);
        lenient().when(order.getCustomer()).thenReturn(customer);
        lenient().when(order.getRestaurant()).thenReturn(restaurant);
        lenient().when(order.getTotal()).thenReturn(new BigDecimal("100"));

        lenient().when(secondOrder.getId()).thenReturn(6L);
        lenient().when(secondOrder.getCustomer()).thenReturn(customer);
        lenient().when(secondOrder.getRestaurant()).thenReturn(restaurant);
        lenient().when(secondOrder.getTotal()).thenReturn(new BigDecimal("100"));

        lenient().when(milestone.getId()).thenReturn(10L);
        lenient().when(milestone.getIsRepeating()).thenReturn(true);
        lenient().when(milestone.getMinOrderAmount()).thenReturn(null);
        lenient().when(milestone.getRequiredVisits()).thenReturn(5);
        lenient().when(milestone.getName()).thenReturn("Five visits");

        when(milestoneRepository.findActiveMilestonesForRestaurant(1L)).thenReturn(List.of(milestone));
        when(redemptionRepository.findByMilestoneIdAndCustomerId(10L, 9L))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(redemptionRepository.save(any(MilestoneRedemption.class))).thenAnswer(inv -> {
            stored.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
    }

    @Test
    @DisplayName("the same order replayed does not count a second visit")
    void duplicateOrderCountsOnce() {
        milestoneService.processOrderCompletion(order);
        milestoneService.processOrderCompletion(order); // replayed event

        assertThat(stored.get().getCurrentVisits()).isEqualTo(1);
        assertThat(stored.get().getLastVisitOrder()).isSameAs(order);
    }

    @Test
    @DisplayName("distinct orders each count a visit")
    void distinctOrdersEachCount() {
        milestoneService.processOrderCompletion(order);
        milestoneService.processOrderCompletion(secondOrder);

        assertThat(stored.get().getCurrentVisits()).isEqualTo(2);
        assertThat(stored.get().getLastVisitOrder()).isSameAs(secondOrder);
    }
}
