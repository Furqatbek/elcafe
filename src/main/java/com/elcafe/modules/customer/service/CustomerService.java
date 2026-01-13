package com.elcafe.modules.customer.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.CreateCustomerRequest;
import com.elcafe.modules.customer.dto.UpdateConsumerProfileRequest;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.marketing.event.MarketingEventPublisher;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final MarketingEventPublisher marketingEventPublisher;

    @Autowired
    @Lazy
    private ReferralService referralService;

    @Transactional
    public Customer createCustomer(Customer customer) {
        log.info("Creating customer: {}", customer.getEmail());
        return customerRepository.save(customer);
    }

    /**
     * Create customer with optional referral code processing
     */
    @Transactional
    public Customer createCustomerWithReferral(CreateCustomerRequest request) {
        log.info("Creating customer with referral support: {}", request.getEmail());

        // Build customer entity from request
        Customer customer = Customer.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .defaultAddress(request.getDefaultAddress())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .notes(request.getNotes())
                .tags(request.getTags())
                .birthDate(request.getBirthDate())
                .language(request.getLanguage())
                .registrationSource(request.getRegistrationSource())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        // Save customer first
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Customer created with ID: {}", savedCustomer.getId());

        // Process referral code if provided (customer was referred by someone)
        String referralCode = request.getReferralCode();

        if (referralCode != null && !referralCode.trim().isEmpty()) {
            try {
                log.info("Processing referral code: {} for customer: {}",
                        referralCode, savedCustomer.getId());

                // Use the method that auto-detects restaurant from the code
                referralService.processReferralByCode(referralCode.trim(), savedCustomer.getId());
                log.info("Referral successfully applied for customer: {}", savedCustomer.getId());
            } catch (Exception e) {
                // Log the error but don't fail customer creation
                log.warn("Failed to apply referral code '{}' for customer {}: {}",
                        referralCode, savedCustomer.getId(), e.getMessage());
                // We don't throw the exception - customer is still created
            }
        }

        // Auto-generate a referral code for the new customer so they can invite others
        try {
            Restaurant restaurant = restaurantRepository.findAll().stream().findFirst().orElse(null);
            if (restaurant != null) {
                referralService.generateReferralCode(restaurant.getId(), savedCustomer.getId());
                log.info("Referral code auto-generated for new customer: {}", savedCustomer.getId());
            } else {
                log.warn("No restaurant found - skipping referral code generation for customer: {}", savedCustomer.getId());
            }
        } catch (Exception e) {
            // Log the error but don't fail customer creation
            log.warn("Failed to auto-generate referral code for customer {}: {}",
                    savedCustomer.getId(), e.getMessage());
        }

        // Publish customer registration event for marketing automation (welcome SMS, etc.)
        try {
            marketingEventPublisher.publishCustomerRegistered(savedCustomer);
        } catch (Exception e) {
            log.warn("Failed to publish customer registration event: {}", e.getMessage());
        }

        return savedCustomer;
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer customerData) {
        log.info("Updating customer: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        customer.setFirstName(customerData.getFirstName());
        customer.setLastName(customerData.getLastName());
        customer.setEmail(customerData.getEmail());
        customer.setPhone(customerData.getPhone());
        customer.setDefaultAddress(customerData.getDefaultAddress());
        customer.setCity(customerData.getCity());
        customer.setState(customerData.getState());
        customer.setZipCode(customerData.getZipCode());
        customer.setNotes(customerData.getNotes());
        customer.setTags(customerData.getTags());
        customer.setActive(customerData.getActive());

        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
    }

    @Transactional(readOnly = true)
    public Page<Customer> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        log.info("Deleting customer: {}", id);
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customerRepository.delete(customer);
    }

    /**
     * Update consumer's own profile (for authenticated consumers)
     * Only updates provided fields (partial update)
     */
    @Transactional
    public Customer updateConsumerProfile(Long customerId, UpdateConsumerProfileRequest request) {
        log.info("Consumer updating own profile: customerId={}", customerId);

        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));

        // Only update fields that are provided
        if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
            customer.setFirstName(request.getFirstName().trim());
        }

        if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
            customer.setLastName(request.getLastName().trim());
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            customer.setEmail(request.getEmail().trim());
        }

        if (request.getPhone() != null && !request.getPhone().trim().isEmpty()) {
            customer.setPhone(request.getPhone().trim());
        }

        if (request.getBirthDate() != null) {
            customer.setBirthDate(request.getBirthDate());
        }

        if (request.getLanguage() != null && !request.getLanguage().trim().isEmpty()) {
            customer.setLanguage(request.getLanguage().trim().toLowerCase());
        }

        if (request.getDefaultAddress() != null) {
            customer.setDefaultAddress(request.getDefaultAddress().trim());
        }

        if (request.getCity() != null) {
            customer.setCity(request.getCity().trim());
        }

        if (request.getState() != null) {
            customer.setState(request.getState().trim());
        }

        if (request.getZipCode() != null) {
            customer.setZipCode(request.getZipCode().trim());
        }

        Customer updated = customerRepository.save(customer);
        log.info("Consumer profile updated successfully: customerId={}", customerId);
        return updated;
    }

    /**
     * Find customer by phone number.
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String phone) {
        return customerRepository.findByPhone(phone);
    }
}
