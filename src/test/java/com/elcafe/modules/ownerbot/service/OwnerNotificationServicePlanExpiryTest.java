package com.elcafe.modules.ownerbot.service;

import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.ownerbot.entity.OwnerNotificationLog;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.ownerbot.repository.OwnerNotificationLogRepository;
import com.elcafe.modules.ownerbot.repository.OwnerTelegramSubscriberRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OwnerNotificationServicePlanExpiryTest {

    @Mock private OwnerTelegramBotService botService;
    @Mock private OwnerTelegramSubscriberRepository subscriberRepository;
    @Mock private OwnerNotificationLogRepository logRepository;
    @Mock private ExpenseRepository expenseRepository;

    private OwnerNotificationService service;

    @BeforeEach
    void setUp() {
        service = new OwnerNotificationService(botService, subscriberRepository, logRepository, expenseRepository);
        when(logRepository.save(any(OwnerNotificationLog.class))).thenAnswer(i -> i.getArgument(0));
        when(botService.sendMessage(anyLong(), anyString())).thenReturn(1);
    }

    private Restaurant restaurant(String planName) {
        SubscriptionPlan plan = mock(SubscriptionPlan.class);
        when(plan.getName()).thenReturn(planName);
        Restaurant r = mock(Restaurant.class);
        when(r.getId()).thenReturn(1L);
        when(r.getPlan()).thenReturn(plan);
        return r;
    }

    private OwnerTelegramSubscriber verifiedSubscriber(long telegramId) {
        OwnerTelegramSubscriber s = new OwnerTelegramSubscriber();
        s.setTelegramUserId(telegramId);
        s.setIsActive(true);
        s.setIsVerified(true);
        return s;
    }

    @Test
    @DisplayName("expiring soon → sends to every verified owner subscriber with the plan name + days")
    void expiringSoon_sendsToVerifiedSubscribers() {
        when(subscriberRepository.findByRestaurantIdAndIsActiveTrueAndIsVerifiedTrue(1L))
                .thenReturn(List.of(verifiedSubscriber(999L)));

        service.notifyPlanExpiry(restaurant("Pro"), 3L);

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(eq(999L), msg.capture());
        assertThat(msg.getValue()).contains("Pro").contains("3");
    }

    @Test
    @DisplayName("expired but in grace → message mentions the grace window")
    void expired_inGrace_graceMessage() {
        when(subscriberRepository.findByRestaurantIdAndIsActiveTrueAndIsVerifiedTrue(1L))
                .thenReturn(List.of(verifiedSubscriber(999L)));

        service.notifyPlanExpiry(restaurant("Pro"), -1L); // 1 day past expiry → grace 2 days left

        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        verify(botService).sendMessage(eq(999L), msg.capture());
        assertThat(msg.getValue()).contains("истекла").contains("2");
    }

    @Test
    @DisplayName("no subscribers → nothing is sent")
    void noSubscribers_noSend() {
        when(subscriberRepository.findByRestaurantIdAndIsActiveTrueAndIsVerifiedTrue(1L))
                .thenReturn(List.of());

        service.notifyPlanExpiry(restaurant("Pro"), 0L);

        verify(botService, never()).sendMessage(anyLong(), anyString());
    }
}
