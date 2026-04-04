package com.elcafe.modules.pos.tax.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.pos.tax.dto.*;
import com.elcafe.modules.pos.tax.entity.TaxExemptionLog;
import com.elcafe.modules.pos.tax.entity.TaxExemptionType;
import com.elcafe.modules.pos.tax.repository.TaxExemptionLogRepository;
import com.elcafe.modules.pos.tax.repository.TaxExemptionTypeRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaxExemptionServiceTest {

    @Mock private TaxExemptionTypeRepository exemptionTypeRepository;
    @Mock private TaxExemptionLogRepository exemptionLogRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private TaxExemptionService taxExemptionService;

    @Captor private ArgumentCaptor<TaxExemptionLog> logCaptor;

    private Restaurant restaurant;
    private TaxExemptionType exemptionType;
    private Order order;
    private User operator;
    private Customer customer;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        exemptionType = TaxExemptionType.builder()
                .id(1L).restaurant(restaurant).name("Government")
                .exemptionCode("GOV-001").requiresDocumentation(true).isActive(true).build();

        order = new Order();
        order.setId(1L);
        order.setRestaurant(restaurant);
        order.setSubtotal(new BigDecimal("100000"));
        order.setTax(new BigDecimal("12000"));
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setServiceFee(BigDecimal.ZERO);
        order.setEntryFee(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setBonusUsed(BigDecimal.ZERO);
        order.setTipAmount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("112000"));

        operator = new User();
        operator.setId(1L);
        operator.setEmail("admin@test.com");

        customer = new Customer();
        customer.setId(1L);
        customer.setPhone("+998901234567");
        customer.setIsTaxExempt(false);
    }

    @Test @DisplayName("createExemptionType — success")
    void createExemptionType_success() {
        CreateTaxExemptionTypeRequest request = new CreateTaxExemptionTypeRequest();
        request.setName("Diplomatic");
        request.setExemptionCode("DIP-001");
        request.setRequiresDocumentation(true);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(exemptionTypeRepository.existsByRestaurantIdAndName(1L, "Diplomatic")).thenReturn(false);
        when(exemptionTypeRepository.save(any())).thenAnswer(i -> { TaxExemptionType t = i.getArgument(0); t.setId(2L); return t; });

        TaxExemptionType result = taxExemptionService.createExemptionType(1L, request);

        assertThat(result.getName()).isEqualTo("Diplomatic");
        verify(exemptionTypeRepository).save(any());
    }

    @Test @DisplayName("getExemptionTypes — returns active list")
    void getExemptionTypes_returnsList() {
        when(exemptionTypeRepository.findByRestaurantIdAndIsActiveTrue(1L)).thenReturn(List.of(exemptionType));

        List<TaxExemptionType> result = taxExemptionService.getExemptionTypes(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Government");
    }

    @Test @DisplayName("applyOrderTaxExemption — success")
    void applyExemption_success() {
        ApplyTaxExemptionRequest request = new ApplyTaxExemptionRequest();
        request.setExemptionTypeId(1L);
        request.setExemptionNumber("EX-123");
        request.setReason("Government order");

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(exemptionTypeRepository.findById(1L)).thenReturn(Optional.of(exemptionType));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(exemptionLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TaxExemptionResult result = taxExemptionService.applyOrderTaxExemption(1L, request, 1L);

        assertThat(result.getTaxExempted()).isEqualByComparingTo("12000");
        assertThat(result.getNewTotal()).isEqualByComparingTo("100000");
        assertThat(order.getTax()).isEqualByComparingTo("0");
        verify(exemptionLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getExemptionNumber()).isEqualTo("EX-123");
    }

    @Test @DisplayName("applyOrderTaxExemption — order not found throws")
    void applyExemption_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxExemptionService.applyOrderTaxExemption(99L, new ApplyTaxExemptionRequest(), 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }

    @Test @DisplayName("applyOrderTaxExemption — invalid type throws")
    void applyExemption_invalidType_throws() {
        ApplyTaxExemptionRequest request = new ApplyTaxExemptionRequest();
        request.setExemptionTypeId(99L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(exemptionTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taxExemptionService.applyOrderTaxExemption(1L, request, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Exemption type not found");
    }

    @Test @DisplayName("removeOrderTaxExemption — recalculates tax")
    void removeExemption_success() {
        order.setIsTaxExempt(true);
        order.setTax(BigDecimal.ZERO);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Order result = taxExemptionService.removeOrderTaxExemption(1L, new BigDecimal("0.12"));

        assertThat(result.getIsTaxExempt()).isFalse();
        assertThat(result.getTax()).isEqualByComparingTo("12000");
    }

    @Test @DisplayName("getExemptionLogs — returns paginated")
    void getExemptionLogs_returnsList() {
        TaxExemptionLog log = TaxExemptionLog.builder().id(1L).restaurant(restaurant)
                .taxAmountExempted(new BigDecimal("12000")).build();
        when(exemptionLogRepository.findByRestaurantIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        Page<TaxExemptionLog> result = taxExemptionService.getExemptionLogs(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("checkCustomerExemption — returns status")
    void checkCustomerExemption() {
        customer.setIsTaxExempt(true);
        customer.setTaxExemptionNumber("EX-123");
        customer.setTaxExemptionTypeId(1L);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(exemptionTypeRepository.findById(1L)).thenReturn(Optional.of(exemptionType));

        TaxExemptCheckResult result = taxExemptionService.checkCustomerExemption(1L);

        assertThat(result.isTaxExempt()).isTrue();
        assertThat(result.getExemptionTypeName()).isEqualTo("Government");
    }

    @Test @DisplayName("getTotalExemptedAmount — returns sum")
    void getTotalExemptedAmount() {
        OffsetDateTime start = OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = OffsetDateTime.now();
        when(exemptionLogRepository.sumExemptedAmountByRestaurantAndDateRange(1L, start, end))
                .thenReturn(new BigDecimal("50000"));

        BigDecimal result = taxExemptionService.getTotalExemptedAmount(1L, start, end);

        assertThat(result).isEqualByComparingTo("50000");
    }

    @Test @DisplayName("setCustomerTaxExempt — marks customer exempt")
    void setCustomerTaxExempt_success() {
        SetCustomerTaxExemptRequest request = new SetCustomerTaxExemptRequest();
        request.setExemptionTypeId(1L);
        request.setExemptionNumber("EX-456");

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(exemptionTypeRepository.findById(1L)).thenReturn(Optional.of(exemptionType));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Customer result = taxExemptionService.setCustomerTaxExempt(1L, request);

        assertThat(result.getIsTaxExempt()).isTrue();
        assertThat(result.getTaxExemptionNumber()).isEqualTo("EX-456");
        assertThat(result.getTaxExemptionTypeId()).isEqualTo(1L);
        verify(customerRepository).save(customer);
    }

    @Test @DisplayName("removeCustomerTaxExempt — clears exempt fields")
    void removeCustomerTaxExempt_success() {
        customer.setIsTaxExempt(true);
        customer.setTaxExemptionNumber("EX-456");
        customer.setTaxExemptionTypeId(1L);

        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Customer result = taxExemptionService.removeCustomerTaxExempt(1L);

        assertThat(result.getIsTaxExempt()).isFalse();
        assertThat(result.getTaxExemptionTypeId()).isNull();
        assertThat(result.getTaxExemptionNumber()).isNull();
        verify(customerRepository).save(customer);
    }
}
