package uz.megahotdog.modules.financial.service;

import uz.megahotdog.modules.notification.service.FinancialOperationAlertService;
import uz.megahotdog.modules.order.entity.Order;
import uz.megahotdog.modules.order.enums.OrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static uz.megahotdog.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
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
        order.setTotal(BigDecimal.valueOf(100000));
        Exception ex = new RuntimeException("DB error");

        recordingService.handleRevenueRecordingFailure(ex, order);

        verify(alertService).alertRevenueRecordingFailure(
                anyLong(), anyString(), any(), any(), anyString(), anyInt());
    }
}
