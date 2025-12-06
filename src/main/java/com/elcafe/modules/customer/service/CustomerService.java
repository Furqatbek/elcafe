package com.elcafe.modules.customer.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.UpdateConsumerProfileRequest;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public Customer createCustomer(Customer customer) {
        log.info("Creating customer: {}", customer.getEmail());
        return customerRepository.save(customer);
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
            customer.setLanguage(request.getLanguage().trim());
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
}
