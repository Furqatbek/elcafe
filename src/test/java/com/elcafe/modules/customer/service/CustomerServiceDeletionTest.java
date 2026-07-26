package com.elcafe.modules.customer.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.marketing.event.MarketingEventPublisher;
import com.elcafe.modules.referral.repository.ReferralCodeRepository;
import com.elcafe.modules.referral.repository.ReferralRepository;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * Deleting a customer must first fire {@link CustomerDeletedEvent} so channel modules erase the PII they
 * hold (Instagram/Telegram subscribers), THEN remove the customer — the ordering that keeps a linked
 * subscriber from being left behind, unlinked and unpurgeable, by the old bare {@code delete}.
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceDeletionTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private MarketingEventPublisher marketingEventPublisher;
    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private ReferralCodeRepository referralCodeRepository;
    @Mock private ReferralRepository referralRepository;
    @Mock private ReferralService referralService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CustomerService service;

    @Test
    @DisplayName("deleteCustomer publishes the erasure event before removing the customer row")
    void publishesErasureEventBeforeDelete() {
        Customer customer = new Customer();
        when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));

        service.deleteCustomer(7L);

        InOrder inOrder = inOrder(eventPublisher, customerRepository);
        inOrder.verify(eventPublisher).publishEvent(new CustomerDeletedEvent(7L));
        inOrder.verify(customerRepository).delete(customer);
    }
}
