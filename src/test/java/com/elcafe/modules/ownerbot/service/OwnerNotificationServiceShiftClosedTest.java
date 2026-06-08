package com.elcafe.modules.ownerbot.service;

import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationLog;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
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
    @Mock private ExpenseRepository expenseRepository;

    private OwnerNotificationService service;
    private OwnerTelegramSubscriber subscriber;

    @BeforeEach
    void setUp() {
        service = new OwnerNotificationService(botService, subscriberRepository,
                logRepository, expenseRepository);

        subscriber = new OwnerTelegramSubscriber();
        subscriber.setTelegramUserId(12345L);
        subscriber.setIsActive(true);
        subscriber.setIsVerified(true);

        when(subscriberRepository.findActiveSubscribersWithSettings(1L)).thenReturn(List.of(subscriber));
        when(logRepository.save(any(OwnerNotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(botService.sendMessage(anyLong(), anyString())).thenReturn(1);
    }

    @Test
    @DisplayName("Empty 0-minute shift carries no expenses or profit numbers — " +
            "earlier shifts' expenses don't bleed into the closing message")
    void emptyShiftDoesNotInheritDayExpenses() {
        // The original bug report: Malika clocks in & out at 20:48 with 0 orders.
        // The shift has no expenses linked to it (employee_shift_id = S2).
        // Day totals at this restaurant happen to be 18k drawer + 115k other,
        // recorded against an earlier shift — those must NOT appear here.
        when(expenseRepository.sumDrawerExpensesByShift(42L)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumNonDrawerExpensesByShift(42L)).thenReturn(BigDecimal.ZERO);

        service.notifyShiftClosed(1L, 42L, "Malika", "20:48", "20:48",
                0L, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).contains("👤 <b>Malika</b>");
        assertThat(msg).contains("📦 Заказов: 0");
        assertThat(msg).contains("💰 Выручка: 0");
        // No expense numbers, no profit number — nothing the owner could
        // mis-attribute to Malika.
        assertThat(msg).doesNotContain("Из кассы смены");
        assertThat(msg).doesNotContain("Прочие расходы");
        assertThat(msg).doesNotContain("Чистая прибыль");
    }

    @Test
    @DisplayName("Productive shift shows only its OWN expenses (sums scoped by shift id)")
    void productiveShiftShowsOwnExpenses() {
        // Shift 100 had real activity: 12 orders, 250k revenue, 5k drawer
        // expense + 3k other expense linked to it.
        when(expenseRepository.sumDrawerExpensesByShift(100L)).thenReturn(new BigDecimal("5000"));
        when(expenseRepository.sumNonDrawerExpensesByShift(100L)).thenReturn(new BigDecimal("3000"));

        service.notifyShiftClosed(1L, 100L, "Aziz", "09:00", "17:00",
                480L, 12, new BigDecimal("250000"),
                new BigDecimal("150000"), new BigDecimal("100000"), null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).contains("👤 <b>Aziz</b>");
        assertThat(msg).contains("📦 Заказов: 12");
        assertThat(msg).contains("💰 Выручка: 250,000.00");
        assertThat(msg).contains("💵 Наличные: 150,000.00");
        assertThat(msg).contains("💳 Карта: 100,000.00");
        assertThat(msg).contains("🪙 Из кассы смены: 5,000.00");
        assertThat(msg).contains("🏦 Прочие расходы: 3,000.00");
        // Profit = sales - shift expenses = 250000 - 5000 - 3000 = 242000.
        // Crucially NOT the restaurant's day-net of 937k.
        assertThat(msg).contains("📈 Чистая прибыль (смена): 242,000.00");
    }

    @Test
    @DisplayName("Shift with sales but no own expenses still shows the profit line")
    void salesWithoutShiftExpenses() {
        when(expenseRepository.sumDrawerExpensesByShift(100L)).thenReturn(BigDecimal.ZERO);
        when(expenseRepository.sumNonDrawerExpensesByShift(100L)).thenReturn(BigDecimal.ZERO);

        service.notifyShiftClosed(1L, 100L, "Dilshod", "09:00", "17:00",
                480L, 5, new BigDecimal("80000"),
                new BigDecimal("80000"), BigDecimal.ZERO, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).doesNotContain("Из кассы смены");
        assertThat(msg).doesNotContain("Прочие расходы");
        assertThat(msg).contains("📈 Чистая прибыль (смена): 80,000.00");
    }

    @Test
    @DisplayName("Negative profit (expenses > sales) gets the 📉 emoji")
    void negativeProfit() {
        when(expenseRepository.sumDrawerExpensesByShift(100L)).thenReturn(new BigDecimal("50000"));
        when(expenseRepository.sumNonDrawerExpensesByShift(100L)).thenReturn(BigDecimal.ZERO);

        service.notifyShiftClosed(1L, 100L, "Aziz", "09:00", "10:00",
                60L, 1, new BigDecimal("10000"),
                new BigDecimal("10000"), BigDecimal.ZERO, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).contains("📉 Чистая прибыль (смена): -40,000.00");
    }

    @Test
    @DisplayName("Null shiftId (legacy path) gracefully degrades to no expense block")
    void nullShiftId() {
        service.notifyShiftClosed(1L, null, "Legacy", "09:00", "10:00",
                60L, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(any(Long.class), captor.capture());
        String msg = captor.getValue();

        assertThat(msg).doesNotContain("Из кассы смены");
        assertThat(msg).doesNotContain("Прочие расходы");
        assertThat(msg).doesNotContain("Чистая прибыль");
    }
}
