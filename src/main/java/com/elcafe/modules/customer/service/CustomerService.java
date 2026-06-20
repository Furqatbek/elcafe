package com.elcafe.modules.customer.service;

import com.elcafe.common.tenant.TenantContext;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.CreateCustomerRequest;
import com.elcafe.modules.customer.dto.CustomerResponse;
import com.elcafe.modules.customer.dto.UpdateConsumerProfileRequest;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.marketing.event.MarketingEventPublisher;
import com.elcafe.modules.referral.entity.ReferralCode;
import com.elcafe.modules.referral.enums.ReferralStatus;
import com.elcafe.modules.referral.repository.ReferralCodeRepository;
import com.elcafe.modules.referral.repository.ReferralRepository;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final MarketingEventPublisher marketingEventPublisher;
    private final CustomerLoyaltyRepository customerLoyaltyRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralRepository referralRepository;
    @Lazy private final ReferralService referralService;

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
        // Convert empty strings to null for unique constraint fields (email)
        Customer customer = Customer.builder()
                .restaurantId(resolveRestaurantId(request.getRestaurantId()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(emptyToNull(request.getEmail()))
                .phone(request.getPhone())
                .defaultAddress(emptyToNull(request.getDefaultAddress()))
                .city(emptyToNull(request.getCity()))
                .state(emptyToNull(request.getState()))
                .zipCode(emptyToNull(request.getZipCode()))
                .notes(emptyToNull(request.getNotes()))
                .tags(emptyToNull(request.getTags()))
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
            Restaurant restaurant = restaurantRepository.findAnyActiveRestaurant().orElse(null);
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
        customer.setEmail(emptyToNull(customerData.getEmail()));
        customer.setPhone(customerData.getPhone());
        customer.setDefaultAddress(emptyToNull(customerData.getDefaultAddress()));
        customer.setCity(emptyToNull(customerData.getCity()));
        customer.setState(emptyToNull(customerData.getState()));
        customer.setZipCode(emptyToNull(customerData.getZipCode()));
        customer.setNotes(emptyToNull(customerData.getNotes()));
        customer.setTags(emptyToNull(customerData.getTags()));
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
        return listForTenant(pageable);
    }

    /**
     * List customers for the caller's tenant (V150: closes the cross-tenant PII leak). An unscoped
     * caller (SUPER_ADMIN, or OFF enforcement mode) still sees all customers.
     */
    private Page<Customer> listForTenant(Pageable pageable) {
        Long tenantId = TenantContext.getRestaurantId();
        return tenantId != null
                ? customerRepository.findByRestaurantId(tenantId, pageable)
                : customerRepository.findAll(pageable);
    }

    /**
     * Get all customers with marketing data (loyalty, referral info)
     */
    @Transactional(readOnly = true)
    public Page<CustomerResponse> getAllCustomersWithMarketing(Pageable pageable) {
        Page<Customer> customerPage = listForTenant(pageable);
        List<Customer> customers = customerPage.getContent();

        if (customers.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Get customer IDs
        Set<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .collect(Collectors.toSet());

        // Fetch loyalty data in bulk using proper query
        Map<Long, CustomerLoyalty> loyaltyMap = customerLoyaltyRepository.findByCustomerIdIn(customerIds).stream()
                .collect(Collectors.toMap(
                        cl -> cl.getCustomer().getId(),
                        Function.identity(),
                        (a, b) -> a
                ));

        // Fetch referral codes in bulk using proper query
        Map<Long, ReferralCode> referralCodeMap = referralCodeRepository.findByCustomerIdIn(customerIds).stream()
                .collect(Collectors.toMap(
                        rc -> rc.getCustomer().getId(),
                        Function.identity(),
                        (a, b) -> a // In case of duplicates, keep the first
                ));

        // Count successful referrals per customer using proper query
        Map<Long, Long> referralSuccessMap = referralRepository.findByReferralCodeCustomerIdInAndStatus(customerIds, ReferralStatus.COMPLETED).stream()
                .collect(Collectors.groupingBy(
                        r -> r.getReferralCode().getCustomer().getId(),
                        Collectors.counting()
                ));

        // Build response list
        List<CustomerResponse> responses = customers.stream()
                .map(customer -> {
                    CustomerResponse response = CustomerResponse.from(customer);

                    // Add loyalty data
                    CustomerLoyalty loyalty = loyaltyMap.get(customer.getId());
                    if (loyalty != null) {
                        response.setBonusBalance(loyalty.getCurrentBalance());
                        response.setLifetimeEarned(loyalty.getLifetimeEarned());
                        response.setLifetimeSpent(loyalty.getLifetimeSpent());
                        response.setTotalSpent(loyalty.getTotalSpent());
                        response.setOrderCount(loyalty.getOrderCount());
                        response.setLastOrderDate(loyalty.getLastOrderDate());
                        if (loyalty.getTier() != null) {
                            response.setTierName(loyalty.getTier().getName());
                        }
                    } else {
                        // Default values for customers without loyalty record
                        response.setBonusBalance(BigDecimal.ZERO);
                        response.setLifetimeEarned(BigDecimal.ZERO);
                        response.setLifetimeSpent(BigDecimal.ZERO);
                        response.setTotalSpent(BigDecimal.ZERO);
                        response.setOrderCount(0);
                    }

                    // Add referral data
                    ReferralCode referralCode = referralCodeMap.get(customer.getId());
                    if (referralCode != null) {
                        response.setReferralCode(referralCode.getCode());
                        response.setReferralUsageCount(referralCode.getUsageCount());
                    }

                    // Add referral success count
                    Long successCount = referralSuccessMap.get(customer.getId());
                    response.setReferralSuccessCount(successCount != null ? successCount.intValue() : 0);

                    return response;
                })
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, customerPage.getTotalElements());
    }

    /**
     * Get a single customer with marketing data
     */
    @Transactional(readOnly = true)
    public CustomerResponse getCustomerWithMarketing(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));

        CustomerResponse response = CustomerResponse.from(customer);

        // Add loyalty data
        customerLoyaltyRepository.findByCustomerId(id).ifPresent(loyalty -> {
            response.setBonusBalance(loyalty.getCurrentBalance());
            response.setLifetimeEarned(loyalty.getLifetimeEarned());
            response.setLifetimeSpent(loyalty.getLifetimeSpent());
            response.setTotalSpent(loyalty.getTotalSpent());
            response.setOrderCount(loyalty.getOrderCount());
            response.setLastOrderDate(loyalty.getLastOrderDate());
            if (loyalty.getTier() != null) {
                response.setTierName(loyalty.getTier().getName());
            }
        });

        // Default values if no loyalty record
        if (response.getBonusBalance() == null) {
            response.setBonusBalance(BigDecimal.ZERO);
            response.setLifetimeEarned(BigDecimal.ZERO);
            response.setLifetimeSpent(BigDecimal.ZERO);
            response.setTotalSpent(BigDecimal.ZERO);
            response.setOrderCount(0);
        }

        // Add referral data using proper query
        referralCodeRepository.findByCustomerId(id)
                .ifPresent(referralCode -> {
                    response.setReferralCode(referralCode.getCode());
                    response.setReferralUsageCount(referralCode.getUsageCount());
                });

        // Count successful referrals using proper query
        long successCount = referralRepository.countByReferralCodeCustomerIdAndStatus(id, ReferralStatus.COMPLETED);
        response.setReferralSuccessCount((int) successCount);

        return response;
    }

    @Transactional(readOnly = true)
    public Customer getCustomerByPhone(String phone) {
        return findByPhoneScoped(phone).orElse(null);
    }

    /**
     * Resolve a customer by phone within the caller's tenant (V150). Falls back to the primary
     * record only for an unscoped caller (e.g. SUPER_ADMIN aggregate), where {@code findByPhone}
     * is no longer safe (a phone can map to several per-restaurant rows).
     */
    private Optional<Customer> findByPhoneScoped(String phone) {
        Long tenantId = TenantContext.getRestaurantId();
        return tenantId != null
                ? customerRepository.findByPhoneAndRestaurantId(phone, tenantId)
                : customerRepository.findFirstByPhoneOrderByIdAsc(phone);
    }

    /** Tenant for an admin-created customer: explicit request value, else the caller's tenant. */
    private Long resolveRestaurantId(Long requested) {
        return requested != null ? requested : TenantContext.getRestaurantId();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerByQrCode(String qrCode) {
        return customerRepository.findByQrCode(qrCode)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "qrCode", qrCode));
    }

    @Transactional
    public Customer regenerateQrCode(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        String newCode;
        do {
            newCode = Customer.generateQrCode();
        } while (customerRepository.findByQrCode(newCode).isPresent());
        customer.setQrCode(newCode);
        log.info("Regenerated QR code for customer {}", customerId);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public java.util.List<Customer> searchCustomersByPhone(String phone) {
        Long tenantId = TenantContext.getRestaurantId();
        return tenantId != null
                ? customerRepository.findByPhoneContainingAndRestaurantId(phone, tenantId)
                : customerRepository.findByPhoneContaining(phone);
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
     * Find customer by phone number, scoped to the caller's tenant (V150).
     */
    @Transactional(readOnly = true)
    public Optional<Customer> findByPhone(String phone) {
        return findByPhoneScoped(phone);
    }

    /**
     * Convert empty string to null to avoid unique constraint violations
     */
    private String emptyToNull(String value) {
        return (value == null || value.trim().isEmpty()) ? null : value.trim();
    }
}
