package com.elcafe.modules.instagram.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.repository.InstagramBotConfigRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberAddressRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.restaurant.repository.BusinessHoursRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The right-to-erasure path the channel lacked: deleting a subscriber removes their addresses AND the
 * subscriber row (not just a block flag), scoped to the caller's tenant; and deleting a customer erases
 * every Instagram subscriber linked to them, so no PII survives unlinked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InstagramBotServiceDeletionTest {

    private static final Long RESTAURANT = 3L;

    @Mock private InstagramBotConfigRepository configRepository;
    @Mock private InstagramSubscriberRepository subscriberRepository;
    @Mock private InstagramSubscriberAddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    // Unused here — none of these tests exercise handleIncomingMessage — declared so @InjectMocks
    // constructor-wires a real mock rather than leaving the field null.
    @Mock private BusinessHoursRepository businessHoursRepository;
    @Mock private InstagramApiClient apiClient;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private InstagramMessageLogger messageLogger;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private InstagramBotService service;

    private InstagramSubscriber subscriber(Long id) {
        return InstagramSubscriber.builder().id(id).restaurantId(RESTAURANT).igsid("ig-" + id).build();
    }

    @Test
    @DisplayName("deleteSubscriber erases the addresses then the subscriber, tenant-scoped")
    void deleteSubscriberErasesAddressesThenSubscriber() {
        InstagramSubscriber s = subscriber(1L);
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(1L, RESTAURANT)).thenReturn(Optional.of(s));

        service.deleteSubscriber(1L);

        InOrder inOrder = inOrder(addressRepository, messageLogger, subscriberRepository);
        inOrder.verify(addressRepository).deleteBySubscriber(s);   // children first
        inOrder.verify(messageLogger).eraseSubscriberLogs(s);      // then their message-log PII (igsid + text)
        inOrder.verify(subscriberRepository).delete(s);
    }

    @Test
    @DisplayName("a foreign subscriber id reads as not-found and nothing is deleted (IDOR closed)")
    void foreignIdIsNotFound() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(RESTAURANT);
        when(subscriberRepository.findByIdAndRestaurantId(99L, RESTAURANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSubscriber(99L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(subscriberRepository, never()).delete(any());
        verify(addressRepository, never()).deleteBySubscriber(any());
        verify(messageLogger, never()).eraseSubscriberLogs(any());
    }

    @Test
    @DisplayName("a customer deletion erases every Instagram subscriber linked to them")
    void customerDeletionErasesLinkedSubscribers() {
        InstagramSubscriber a = subscriber(1L);
        InstagramSubscriber b = subscriber(2L);
        when(subscriberRepository.findByCustomerId(50L)).thenReturn(List.of(a, b));

        service.onCustomerDeleted(new CustomerDeletedEvent(50L));

        verify(addressRepository).deleteBySubscriber(a);
        verify(addressRepository).deleteBySubscriber(b);
        verify(messageLogger).eraseSubscriberLogs(a);
        verify(messageLogger).eraseSubscriberLogs(b);
        verify(subscriberRepository).delete(a);
        verify(subscriberRepository).delete(b);
    }
}
