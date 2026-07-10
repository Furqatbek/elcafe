package com.elcafe.modules.customer.service;

import com.elcafe.modules.customer.dto.CustomerActivityDTO;
import com.elcafe.modules.customer.dto.CustomerActivityFilterDTO;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerActivityServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @InjectMocks private CustomerActivityService customerActivityService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(1L).phone("+998901234567")
                .firstName("Test").lastName("Customer").active(true).build();
        // Two grouped queries replace the old three-per-customer lookups
        when(orderRepository.findCustomerActivityRows()).thenReturn(List.of());
        when(orderRepository.findCustomerOrderSources()).thenReturn(List.of());
    }

    @Test @DisplayName("getAllCustomersActivity — returns activity list")
    void getAllCustomersActivity_returnsList() {
        when(customerRepository.findByActiveTrue()).thenReturn(List.of(customer));

        List<CustomerActivityDTO> result = customerActivityService.getAllCustomersActivity();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(1L);
        assertThat(result.get(0).getFrequency()).isEqualTo(0);
    }

    @Test @DisplayName("getAllCustomersActivity — maps the aggregate row onto the DTO")
    void getAllCustomersActivity_mapsAggregates() {
        when(customerRepository.findByActiveTrue()).thenReturn(List.of(customer));
        java.time.OffsetDateTime lastOrder = java.time.OffsetDateTime.now().minusDays(3);
        when(orderRepository.findCustomerActivityRows()).thenReturn(List.of(
                new com.elcafe.modules.order.dto.CustomerActivityRow(1L, 4L, new BigDecimal("200"), lastOrder)));
        when(orderRepository.findCustomerOrderSources()).thenReturn(List.of(
                new com.elcafe.modules.order.dto.CustomerSourceRow(1L, com.elcafe.modules.order.enums.OrderSource.WAITER)));

        CustomerActivityDTO dto = customerActivityService.getAllCustomersActivity().get(0);

        assertThat(dto.getFrequency()).isEqualTo(4);
        assertThat(dto.getMonetary()).isEqualByComparingTo("200");
        assertThat(dto.getAverageCheck()).isEqualByComparingTo("50");
        assertThat(dto.getRecency()).isEqualTo(3);
        assertThat(dto.getOrderSources()).containsExactly(com.elcafe.modules.order.enums.OrderSource.WAITER);
    }

    @Test @DisplayName("getFilteredCustomersActivity — applies filter")
    void getFilteredCustomersActivity_filtersCorrectly() {
        when(customerRepository.findByActiveTrue()).thenReturn(List.of(customer));
        CustomerActivityFilterDTO filter = new CustomerActivityFilterDTO();
        filter.setActive(true);

        List<CustomerActivityDTO> result = customerActivityService.getFilteredCustomersActivity(filter);

        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("getFilteredCustomersActivity — empty filter returns all")
    void getFilteredCustomersActivity_emptyFilter() {
        when(customerRepository.findByActiveTrue()).thenReturn(List.of(customer));
        CustomerActivityFilterDTO filter = new CustomerActivityFilterDTO();

        List<CustomerActivityDTO> result = customerActivityService.getFilteredCustomersActivity(filter);

        assertThat(result).hasSize(1);
    }
}
