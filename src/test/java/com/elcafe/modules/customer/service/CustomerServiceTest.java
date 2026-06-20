package com.elcafe.modules.customer.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.CreateCustomerRequest;
import com.elcafe.modules.customer.dto.CustomerResponse;
import com.elcafe.modules.customer.dto.UpdateConsumerProfileRequest;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.marketing.event.MarketingEventPublisher;
import com.elcafe.modules.referral.repository.ReferralCodeRepository;
import com.elcafe.modules.referral.repository.ReferralRepository;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomerServiceTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private MarketingEventPublisher marketingEventPublisher;
    @Mock private CustomerLoyaltyRepository customerLoyaltyRepository;
    @Mock private ReferralCodeRepository referralCodeRepository;
    @Mock private ReferralRepository referralRepository;
    @Mock private ReferralService referralService;
    @InjectMocks private CustomerService customerService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(1L).phone("+998901234567")
                .firstName("Test").lastName("Customer").email("test@example.com")
                .active(true).build();
    }

    @Test @DisplayName("createCustomer — saves and returns")
    void createCustomer_success() {
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> { Customer c = i.getArgument(0); c.setId(1L); return c; });
        Customer result = customerService.createCustomer(customer);
        assertThat(result.getId()).isEqualTo(1L);
        verify(customerRepository).save(customer);
    }

    @Test @DisplayName("createCustomerWithReferral — creates + processes referral")
    void createCustomerWithReferral_success() {
        CreateCustomerRequest req = new CreateCustomerRequest();
        req.setFirstName("New"); req.setLastName("User"); req.setPhone("+998909876543");
        req.setEmail("new@test.com"); req.setReferralCode("REF-ABC");

        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> { Customer c = i.getArgument(0); c.setId(2L); return c; });
        Restaurant restaurant = new Restaurant(); restaurant.setId(1L);
        when(restaurantRepository.findAnyActiveRestaurant()).thenReturn(Optional.of(restaurant));

        Customer result = customerService.createCustomerWithReferral(req);

        assertThat(result.getId()).isEqualTo(2L);
        verify(referralService).processReferralByCode(eq("REF-ABC"), eq(2L));
        verify(referralService).generateReferralCode(eq(1L), eq(2L));
        verify(marketingEventPublisher).publishCustomerRegistered(any());
    }

    @Test @DisplayName("updateCustomer — updates fields")
    void updateCustomer_success() {
        Customer updateData = Customer.builder().firstName("Updated").lastName("Name")
                .phone("+998901234567").active(true).build();
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.updateCustomer(1L, updateData);
        assertThat(result.getFirstName()).isEqualTo("Updated");
    }

    @Test @DisplayName("getCustomerById — found")
    void getCustomerById_found() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        Customer result = customerService.getCustomerById(1L);
        assertThat(result.getPhone()).isEqualTo("+998901234567");
    }

    @Test @DisplayName("getCustomerById — not found throws")
    void getCustomerById_notFound_throws() {
        when(customerRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> customerService.getCustomerById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("getAllCustomers — paginated")
    void getAllCustomers_returnsPage() {
        when(customerRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1));
        Page<Customer> result = customerService.getAllCustomers(PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getAllCustomersWithMarketing — enriched page")
    void getAllCustomersWithMarketing_returnsPage() {
        when(customerRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(customer), PageRequest.of(0, 10), 1));
        when(customerLoyaltyRepository.findByCustomerIdIn(any(Set.class))).thenReturn(List.of());
        when(referralCodeRepository.findByCustomerIdIn(any(Set.class))).thenReturn(List.of());
        when(referralRepository.findByReferralCodeCustomerIdInAndStatus(any(), any())).thenReturn(List.of());

        Page<CustomerResponse> result = customerService.getAllCustomersWithMarketing(PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getCustomerByPhone — found")
    void getCustomerByPhone_found() {
        when(customerRepository.findFirstByPhoneOrderByIdAsc("+998901234567")).thenReturn(Optional.of(customer));
        Customer result = customerService.getCustomerByPhone("+998901234567");
        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Test");
    }

    @Test @DisplayName("searchCustomersByPhone — returns list")
    void searchCustomersByPhone_returnsList() {
        when(customerRepository.findByPhoneContaining("9012")).thenReturn(List.of(customer));
        List<Customer> result = customerService.searchCustomersByPhone("9012");
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("deleteCustomer — deletes")
    void deleteCustomer_success() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        customerService.deleteCustomer(1L);
        verify(customerRepository).delete(customer);
    }

    @Test @DisplayName("updateConsumerProfile — partial update")
    void updateConsumerProfile_success() {
        UpdateConsumerProfileRequest req = new UpdateConsumerProfileRequest();
        req.setFirstName("Updated"); req.setCity("Samarkand");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.updateConsumerProfile(1L, req);
        assertThat(result.getFirstName()).isEqualTo("Updated");
        assertThat(result.getCity()).isEqualTo("Samarkand");
    }

    @Test @DisplayName("findByPhone — returns optional")
    void findByPhone_returnsOptional() {
        when(customerRepository.findFirstByPhoneOrderByIdAsc("+998901234567")).thenReturn(Optional.of(customer));
        Optional<Customer> result = customerService.findByPhone("+998901234567");
        assertThat(result).isPresent();
    }
}
