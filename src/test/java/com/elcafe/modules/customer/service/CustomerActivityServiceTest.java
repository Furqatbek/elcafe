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
        when(orderRepository.findByCustomer_IdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(orderRepository.sumTotalByCustomerId(anyLong())).thenReturn(BigDecimal.ZERO);
        when(orderRepository.findDistinctOrderSourcesByCustomerId(anyLong())).thenReturn(List.of());
    }

    @Test @DisplayName("getAllCustomersActivity — returns activity list")
    void getAllCustomersActivity_returnsList() {
        when(customerRepository.findByActiveTrue()).thenReturn(List.of(customer));

        List<CustomerActivityDTO> result = customerActivityService.getAllCustomersActivity();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCustomerId()).isEqualTo(1L);
        assertThat(result.get(0).getFrequency()).isEqualTo(0);
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
