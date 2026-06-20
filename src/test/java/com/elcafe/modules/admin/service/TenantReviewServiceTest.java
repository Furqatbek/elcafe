package com.elcafe.modules.admin.service;

import com.elcafe.common.tenant.AssignmentConfidence;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantReviewServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private WaiterRepository waiterRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private TenantReviewService service;

    @Test
    void reassignCustomer_setsRestaurantAndMarksReviewed() {
        Customer c = new Customer();
        c.setId(1L);
        c.setRestaurantId(5L);
        c.setTenantAssignmentConfidence(AssignmentConfidence.LOW);
        when(restaurantRepository.existsById(9L)).thenReturn(true);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(c));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = service.reassignCustomer(1L, 9L);

        assertThat(result.getRestaurantId()).isEqualTo(9L);
        assertThat(result.getTenantAssignmentConfidence()).isEqualTo(AssignmentConfidence.HIGH);
    }

    @Test
    void reassignCustomer_unknownRestaurant_throwsAndDoesNotSave() {
        when(restaurantRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.reassignCustomer(1L, 99L))
                .isInstanceOf(BadRequestException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    void reassignWaiter_setsRestaurantAndMarksReviewed() {
        Waiter w = new Waiter();
        w.setId(2L);
        w.setRestaurantId(5L);
        w.setTenantAssignmentConfidence(AssignmentConfidence.LOW);
        when(restaurantRepository.existsById(9L)).thenReturn(true);
        when(waiterRepository.findById(2L)).thenReturn(Optional.of(w));
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));

        Waiter result = service.reassignWaiter(2L, 9L);

        assertThat(result.getRestaurantId()).isEqualTo(9L);
        assertThat(result.getTenantAssignmentConfidence()).isEqualTo(AssignmentConfidence.HIGH);
    }

    @Test
    void lowConfidenceWaiters_returnsRepoResult() {
        Waiter w = new Waiter();
        w.setId(2L);
        when(waiterRepository.findByTenantAssignmentConfidence(AssignmentConfidence.LOW))
                .thenReturn(List.of(w));

        assertThat(service.lowConfidenceWaiters()).hasSize(1);
    }
}
