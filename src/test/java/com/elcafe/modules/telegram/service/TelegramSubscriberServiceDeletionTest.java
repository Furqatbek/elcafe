package com.elcafe.modules.telegram.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramCampaignRecipientRepository;
import com.elcafe.modules.telegram.repository.TelegramLogRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberLocationRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Telegram right-to-erasure: deleting a subscriber removes their PII AND the RESTRICT-FK children
 * (campaign-recipients, logs, locations) that would otherwise refuse the delete — children first — and
 * deleting a customer erases every linked subscriber (which also unblocks the customer delete, since the
 * customer_id FK is ON DELETE RESTRICT).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TelegramSubscriberServiceDeletionTest {

    @Mock private TelegramSubscriberRepository subscriberRepository;
    @Mock private ShiftTimeService shiftTimeService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private TelegramSubscriberLocationRepository locationRepository;
    @Mock private TelegramLogRepository logRepository;
    @Mock private TelegramCampaignRecipientRepository campaignRecipientRepository;

    @InjectMocks private TelegramSubscriberService service;

    private TelegramSubscriber subscriber(Long id) {
        return TelegramSubscriber.builder().id(id).restaurantId(3L).telegramUserId(1000L + id).build();
    }

    @Test
    @DisplayName("deleteSubscriber erases the RESTRICT children before the subscriber")
    void deleteSubscriberErasesChildrenFirst() {
        TelegramSubscriber s = subscriber(1L);
        when(subscriberRepository.findById(1L)).thenReturn(Optional.of(s));

        service.deleteSubscriber(1L);

        InOrder inOrder = inOrder(
                campaignRecipientRepository, logRepository, locationRepository, subscriberRepository);
        inOrder.verify(campaignRecipientRepository).deleteBySubscriber(s);
        inOrder.verify(logRepository).deleteBySubscriber(s);
        inOrder.verify(locationRepository).deleteBySubscriber(s);
        inOrder.verify(subscriberRepository).delete(s);
    }

    @Test
    @DisplayName("a not-found id deletes nothing")
    void notFoundDeletesNothing() {
        when(subscriberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSubscriber(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("a customer deletion erases every Telegram subscriber linked to them")
    void customerDeletionErasesLinkedSubscribers() {
        TelegramSubscriber a = subscriber(1L);
        TelegramSubscriber b = subscriber(2L);
        when(subscriberRepository.findAllByCustomerId(50L)).thenReturn(List.of(a, b));

        service.onCustomerDeleted(new CustomerDeletedEvent(50L));

        verify(subscriberRepository).delete(a);
        verify(subscriberRepository).delete(b);
        verify(logRepository).deleteBySubscriber(a);
        verify(campaignRecipientRepository).deleteBySubscriber(b);
    }
}
