package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.CustomerRetentionDTO;
import com.elcafe.modules.analytics.dto.CustomerLTVDTO;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAnalyticsServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ShiftTimeService shiftTimeService;

    @InjectMocks private CustomerAnalyticsService customerAnalyticsService;

    @Test
    @DisplayName("getCustomerRetention — returns retention data")
    void getRetention_returnsData() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(7),
                        OffsetDateTime.now(ZoneOffset.UTC),
                        java.time.LocalTime.of(0, 0),
                        java.time.LocalTime.of(23, 59)));
        when(orderRepository.findCustomerOrderStats(anyLong(), any(), any(), any()))
                .thenReturn(List.of());

        CustomerRetentionDTO result = customerAnalyticsService.getCustomerRetention(
                LocalDate.now().minusDays(7), LocalDate.now(), 1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getCustomerLTV — returns LTV data")
    void getLTV_returnsData() {
        when(orderRepository.findCustomerLifetimeStats(anyLong(), any())).thenReturn(List.of());

        CustomerLTVDTO result = customerAnalyticsService.getCustomerLTV(1L);
        assertNotNull(result);
    }
}
