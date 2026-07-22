package com.elcafe.modules.customer.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.AddressResponse;
import com.elcafe.modules.customer.dto.CreateAddressRequest;
import com.elcafe.modules.customer.dto.UpdateAddressRequest;
import com.elcafe.modules.customer.entity.Address;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.AddressRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock private AddressRepository addressRepository;
    @Mock private CustomerRepository customerRepository;
    @InjectMocks private AddressService addressService;

    private Customer customer;
    private Address address;

    @BeforeEach
    void setUp() {
        customer = Customer.builder().id(1L).phone("+998901234567")
                .firstName("Test").lastName("Customer").active(true).build();
        address = Address.builder().id(1L).customer(customer).label("Home")
                .displayName("123 Main St").city("Tashkent").isDefault(true).active(true)
                .latitude(41.311081).longitude(69.240562).build();
    }

    @Test @DisplayName("getCustomerAddresses — returns list")
    void getCustomerAddresses_returnsList() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerIdAndActiveTrue(1L)).thenReturn(List.of(address));
        List<AddressResponse> result = addressService.getCustomerAddresses(1L);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("Home");
    }

    @Test @DisplayName("getAddress — found")
    void getAddress_found() {
        when(addressRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(address));
        AddressResponse result = addressService.getAddress(1L, 1L);
        assertThat(result.getDisplayName()).isEqualTo("123 Main St");
    }

    @Test @DisplayName("getAddress — not found throws")
    void getAddress_notFound_throws() {
        when(addressRepository.findByIdAndCustomerId(99L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> addressService.getAddress(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("createAddress — success")
    void createAddress_success() {
        CreateAddressRequest req = new CreateAddressRequest();
        req.setLabel("Work"); req.setCity("Tashkent"); req.setLatitude(41.31); req.setLongitude(69.24);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(addressRepository.save(any())).thenAnswer(i -> { Address a = i.getArgument(0); a.setId(2L); return a; });

        AddressResponse result = addressService.createAddress(1L, req);
        assertThat(result.getLabel()).isEqualTo("Work");
    }

    @Test @DisplayName("updateAddress — updates selectively")
    void updateAddress_success() {
        UpdateAddressRequest req = new UpdateAddressRequest();
        req.setLabel("Updated Home"); req.setCity("Samarkand");
        when(addressRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AddressResponse result = addressService.updateAddress(1L, 1L, req);
        assertThat(result.getLabel()).isEqualTo("Updated Home");
        assertThat(result.getCity()).isEqualTo("Samarkand");
    }

    @Test @DisplayName("deleteAddress — soft deletes")
    void deleteAddress_success() {
        when(addressRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        addressService.deleteAddress(1L, 1L);
        assertThat(address.getActive()).isFalse();
    }

    @Test @DisplayName("setDefaultAddress — clears old and sets new")
    void setDefaultAddress_success() {
        address.setIsDefault(false);
        when(addressRepository.findByIdAndCustomerId(1L, 1L)).thenReturn(Optional.of(address));
        when(addressRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        AddressResponse result = addressService.setDefaultAddress(1L, 1L);
        assertThat(result.getIsDefault()).isTrue();
        verify(addressRepository).unsetDefaultForCustomer(1L, 1L);
    }

    @Test @DisplayName("getDefaultAddress — found")
    void getDefaultAddress_found() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(addressRepository.findByCustomerIdAndIsDefaultTrue(1L)).thenReturn(Optional.of(address));

        AddressResponse result = addressService.getDefaultAddress(1L);
        assertThat(result.getIsDefault()).isTrue();
    }
}
