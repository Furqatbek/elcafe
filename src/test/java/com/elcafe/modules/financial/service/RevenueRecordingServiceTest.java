package com.elcafe.modules.financial.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.notification.service.FinancialOperationAlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RevenueRecordingServiceTest {

    @Mock private RevenueService revenueService;
    @Mock private FinancialOperationAlertService alertService;

    @InjectMocks private RevenueRecordingService recordingService;

    @Test
    @DisplayName("recordRevenueWithRetry — records successfully")
    void recordRevenue_success() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(BigDecimal.valueOf(100000));
        doNothing().when(revenueService).recordOrderRevenue(any());

        assertDoesNotThrow(() -> recordingService.recordRevenueWithRetry(order));
        verify(revenueService).recordOrderRevenue(order);
    }

    @Test
    @DisplayName("recordRevenueSynchronously — returns true on success")
    void recordSynchronously_success() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(BigDecimal.valueOf(100000));
        doNothing().when(revenueService).recordOrderRevenue(any());

        boolean result = recordingService.recordRevenueSynchronously(order);
        assertTrue(result);
    }

    @Test
    @DisplayName("handleRevenueRecordingFailure — sends alert")
    void handleFailure_sendsAlert() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        Exception ex = new RuntimeException("DB error");

        recordingService.handleRevenueRecordingFailure(ex, order);

        verify(alertService).sendRevenueRecordingFailureAlert(any(), any());
    }
}
