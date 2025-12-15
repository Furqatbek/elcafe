package com.elcafe.modules.customer.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.AddressResponse;
import com.elcafe.modules.customer.dto.CreateAddressRequest;
import com.elcafe.modules.customer.dto.UpdateAddressRequest;
import com.elcafe.modules.customer.entity.Address;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.AddressRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    /**
     * Get all addresses for a customer
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getCustomerAddresses(Long customerId) {
        log.info("Fetching addresses for customer: {}", customerId);

        // Verify customer exists
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        List<Address> addresses = addressRepository.findByCustomerIdAndActiveTrue(customerId);

        return addresses.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a specific address by ID
     */
    @Transactional(readOnly = true)
    public AddressResponse getAddress(Long customerId, Long addressId) {
        log.info("Fetching address {} for customer {}", addressId, customerId);

        Address address = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        return convertToResponse(address);
    }

    /**
     * Create a new address for a customer
     */
    @Transactional
    public AddressResponse createAddress(Long customerId, CreateAddressRequest request) {
        log.info("Creating new address for customer: {}", customerId);

        // Verify customer exists
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        // If this is set as default, unset other defaults
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.unsetAllDefaultsForCustomer(customerId);
        }

        // Create address
        Address address = Address.builder()
                .customer(customer)
                .label(request.getLabel())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .placeId(request.getPlaceId())
                .osmType(request.getOsmType())
                .osmId(request.getOsmId())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .addressClass(request.getAddressClass())
                .type(request.getType())
                .displayName(request.getDisplayName())
                .road(request.getRoad())
                .neighbourhood(request.getNeighbourhood())
                .county(request.getCounty())
                .city(request.getCity())
                .state(request.getState())
                .postcode(request.getPostcode())
                .country(request.getCountry())
                .countryCode(request.getCountryCode())
                .boundingBoxMinLat(request.getBoundingBoxMinLat())
                .boundingBoxMaxLat(request.getBoundingBoxMaxLat())
                .boundingBoxMinLon(request.getBoundingBoxMinLon())
                .boundingBoxMaxLon(request.getBoundingBoxMaxLon())
                .deliveryInstructions(request.getDeliveryInstructions())
                .active(true)
                .build();

        address = addressRepository.save(address);

        log.info("Created address {} for customer {}", address.getId(), customerId);

        return convertToResponse(address);
    }

    /**
     * Update an existing address
     */
    @Transactional
    public AddressResponse updateAddress(Long customerId, Long addressId, UpdateAddressRequest request) {
        log.info("Updating address {} for customer {}", addressId, customerId);

        // Find address
        Address address = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        // If setting as default, unset other defaults
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.unsetDefaultForCustomer(customerId, addressId);
        }

        // Update fields (only non-null values)
        if (request.getLabel() != null) {
            address.setLabel(request.getLabel());
        }
        if (request.getIsDefault() != null) {
            address.setIsDefault(request.getIsDefault());
        }
        if (request.getPlaceId() != null) {
            address.setPlaceId(request.getPlaceId());
        }
        if (request.getOsmType() != null) {
            address.setOsmType(request.getOsmType());
        }
        if (request.getOsmId() != null) {
            address.setOsmId(request.getOsmId());
        }
        if (request.getLatitude() != null) {
            address.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            address.setLongitude(request.getLongitude());
        }
        if (request.getAddressClass() != null) {
            address.setAddressClass(request.getAddressClass());
        }
        if (request.getType() != null) {
            address.setType(request.getType());
        }
        if (request.getDisplayName() != null) {
            address.setDisplayName(request.getDisplayName());
        }
        if (request.getRoad() != null) {
            address.setRoad(request.getRoad());
        }
        if (request.getNeighbourhood() != null) {
            address.setNeighbourhood(request.getNeighbourhood());
        }
        if (request.getCounty() != null) {
            address.setCounty(request.getCounty());
        }
        if (request.getCity() != null) {
            address.setCity(request.getCity());
        }
        if (request.getState() != null) {
            address.setState(request.getState());
        }
        if (request.getPostcode() != null) {
            address.setPostcode(request.getPostcode());
        }
        if (request.getCountry() != null) {
            address.setCountry(request.getCountry());
        }
        if (request.getCountryCode() != null) {
            address.setCountryCode(request.getCountryCode());
        }
        if (request.getBoundingBoxMinLat() != null) {
            address.setBoundingBoxMinLat(request.getBoundingBoxMinLat());
        }
        if (request.getBoundingBoxMaxLat() != null) {
            address.setBoundingBoxMaxLat(request.getBoundingBoxMaxLat());
        }
        if (request.getBoundingBoxMinLon() != null) {
            address.setBoundingBoxMinLon(request.getBoundingBoxMinLon());
        }
        if (request.getBoundingBoxMaxLon() != null) {
            address.setBoundingBoxMaxLon(request.getBoundingBoxMaxLon());
        }
        if (request.getDeliveryInstructions() != null) {
            address.setDeliveryInstructions(request.getDeliveryInstructions());
        }
        if (request.getActive() != null) {
            address.setActive(request.getActive());
        }

        address = addressRepository.save(address);

        log.info("Updated address {} for customer {}", addressId, customerId);

        return convertToResponse(address);
    }

    /**
     * Delete an address (soft delete)
     */
    @Transactional
    public void deleteAddress(Long customerId, Long addressId) {
        log.info("Deleting address {} for customer {}", addressId, customerId);

        // Find address
        Address address = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        // Soft delete
        address.setActive(false);
        if (address.getIsDefault()) {
            address.setIsDefault(false);
        }

        addressRepository.save(address);

        log.info("Deleted address {} for customer {}", addressId, customerId);
    }

    /**
     * Set an address as default
     */
    @Transactional
    public AddressResponse setDefaultAddress(Long customerId, Long addressId) {
        log.info("Setting address {} as default for customer {}", addressId, customerId);

        // Find address
        Address address = addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));

        // Unset other defaults
        addressRepository.unsetDefaultForCustomer(customerId, addressId);

        // Set as default
        address.setIsDefault(true);
        address = addressRepository.save(address);

        log.info("Set address {} as default for customer {}", addressId, customerId);

        return convertToResponse(address);
    }

    /**
     * Get default address for a customer
     */
    @Transactional(readOnly = true)
    public AddressResponse getDefaultAddress(Long customerId) {
        log.info("Fetching default address for customer: {}", customerId);

        // Verify customer exists
        customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        Address address = addressRepository.findByCustomerIdAndIsDefaultTrue(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("No default address found for customer"));

        return convertToResponse(address);
    }

    /**
     * Convert Address entity to AddressResponse DTO
     */
    private AddressResponse convertToResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .customerId(address.getCustomer().getId())
                .label(address.getLabel())
                .isDefault(address.getIsDefault())
                .placeId(address.getPlaceId())
                .osmType(address.getOsmType())
                .osmId(address.getOsmId())
                .latitude(address.getLatitude())
                .longitude(address.getLongitude())
                .addressClass(address.getAddressClass())
                .type(address.getType())
                .displayName(address.getDisplayName())
                .road(address.getRoad())
                .neighbourhood(address.getNeighbourhood())
                .county(address.getCounty())
                .city(address.getCity())
                .state(address.getState())
                .postcode(address.getPostcode())
                .country(address.getCountry())
                .countryCode(address.getCountryCode())
                .boundingBoxMinLat(address.getBoundingBoxMinLat())
                .boundingBoxMaxLat(address.getBoundingBoxMaxLat())
                .boundingBoxMinLon(address.getBoundingBoxMinLon())
                .boundingBoxMaxLon(address.getBoundingBoxMaxLon())
                .deliveryInstructions(address.getDeliveryInstructions())
                .active(address.getActive())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}
