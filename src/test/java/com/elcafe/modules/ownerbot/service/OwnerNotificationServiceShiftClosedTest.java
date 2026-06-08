package com.elcafe.modules.ownerbot.service;

import com.elcafe.modules.notification.service.DailyFinancialReportService;
import com.elcafe.modules.notification.service.DailyFinancialReportService.DailyMetrics;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationLog;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.ownerbot.enums.OwnerNotificationType;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationLogRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerNotificationServiceShiftClosedTest {

    @Mock private OwnerTelegramBotService botService;
    @Mock private OwnerTelegramSubscriberRepository subscriberRepository;
    @Mock private OwnerNotificationLogRepository logRepository;
    @Mock private DailyFinancialReportService dailyFinancialReportService;

    private OwnerNotificationService service;
    private OwnerTelegramSubscriber subscriber;

    @BeforeEach
    void setUp() {
        service = new OwnerNotificationService(botService, subscriberRepository,
                logRepository, dailyFinancialReportService);

        subscriber = new OwnerTelegramSubscriber();
        subscriber.setTelegramUserId(12345L);
        subscriber.setIsActive(true);
        subscriber.setIsVerified(true);

        when(subscriberRepository.findActiveSubscribersWithSettings(1L)).thenReturn(List.of(subscriber));
        when(logRepository.save(any(OwnerNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(botService.sendMessage(anyLong(), anyString())).thenReturn(1);
    }

    private DailyMetrics metrics(BigDecimal drawer, BigDecimal other, BigDecimal netIncome) {
        return new DailyMetrics(
                "Test Cafe", LocalDate.now(), LocalDate.now(),
                null, null, null, null,
                0,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, drawer, other, BigDecimal.ZERO, netIncome);
    }

    @Test
    @DisplayName("Day-scoped expenses and profit appear under 'За день' header, " +
            "not attributed to the closing employee")
    void dayScopedNumbersAreLabeledAsDayTotals() {
        when(dailyFinancialReportService.calculateDailyMetrics(anyLong(), any(LocalDate.class)))
                .thenReturn(metrics(
                        new BigDecimal("18000"),
                        new BigDecimal("115000"),
                        new BigDecimal("937000")));

        // The exact scenario from the bug report: Malika opens then immediately
        // closes a shift with no orders. Day totals (drawer/other/profit) come
        // from the WHOLE restaurant day, not from her empty shift.
        service.notifyShiftClosed(1L, "Malika", "20:48", "20:48",
                0L, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).contains("👤 <b>Malika</b>");
        assertThat(msg).contains("📦 Заказов: 0");
        assertThat(msg).contains("💰 Выручка: 0");
        // The day-scoped block must carry its own header so the owner can't
        // misread these numbers as being attributable to Malika.
        assertThat(msg).contains("📊 <b>За день (по ресторану):</b>");
        assertThat(msg).contains("🪙 Из кассы смены: 18,000.00");
        assertThat(msg).contains("🏦 Прочие расходы: 115,000.00");
        assertThat(msg).contains("📈 Чистая прибыль: 937,000.00");

        int dayHeaderAt = msg.indexOf("📊");
        int drawerAt = msg.indexOf("🪙");
        int profitAt = msg.indexOf("Чистая прибыль");
        assertThat(dayHeaderAt).isGreaterThan(0);
        assertThat(drawerAt).isGreaterThan(dayHeaderAt);
        assertThat(profitAt).isGreaterThan(dayHeaderAt);
    }

    @Test
    @DisplayName("No day-totals block when the day's expenses and profit are all zero")
    void omitsDayTotalsWhenAllZero() {
        when(dailyFinancialReportService.calculateDailyMetrics(anyLong(), any(LocalDate.class)))
                .thenReturn(metrics(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        service.notifyShiftClosed(1L, "Malika", "20:48", "20:48",
                0L, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).doesNotContain("📊 <b>За день");
        assertThat(msg).doesNotContain("Чистая прибыль");
    }

    @Test
    @DisplayName("Negative net profit gets the 📉 emoji and still lives in the day block")
    void negativeNetProfitFormatted() {
        when(dailyFinancialReportService.calculateDailyMetrics(anyLong(), any(LocalDate.class)))
                .thenReturn(metrics(
                        new BigDecimal("5000"),
                        new BigDecimal("10000"),
                        new BigDecimal("-15000")));

        service.notifyShiftClosed(1L, "Aziz", "09:00", "17:00",
                480L, 12, new BigDecimal("250000"),
                new BigDecimal("150000"), new BigDecimal("100000"), null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).contains("📉 Чистая прибыль: -15,000.00");
        assertThat(msg).contains("📊 <b>За день (по ресторану):</b>");
        // Shift-scoped sales render under the employee header, day totals below it.
        int salesAt = msg.indexOf("Выручка");
        int dayAt = msg.indexOf("📊");
        assertThat(salesAt).isGreaterThan(0);
        assertThat(dayAt).isGreaterThan(salesAt);
    }
}
