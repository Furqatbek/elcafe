package com.elcafe.modules.admin.service;

import com.elcafe.common.tenant.AssignmentConfidence;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SUPER_ADMIN review surface for low-confidence tenant assignments (Phase 0 §3.7 safeguard for the
 * V148/V150 backfill heuristics). Lists rows the backfill assigned without evidence (the no-evidence
 * "oldest restaurant" fallback) and lets a platform admin reassign them to the correct restaurant —
 * reassignment marks the row HIGH (reviewed), so it drops off the list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantReviewService {

    private final CustomerRepository customerRepository;
    private final WaiterRepository waiterRepository;
    private final RestaurantRepository restaurantRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public Page<Customer> lowConfidenceCustomers(Pageable pageable) {
        return customerRepository.findByTenantAssignmentConfidence(AssignmentConfidence.LOW, pageable);
    }

    @Transactional(readOnly = true)
    public List<Waiter> lowConfidenceWaiters() {
        return waiterRepository.findByTenantAssignmentConfidence(AssignmentConfidence.LOW);
    }

    @Transactional
    public Customer reassignCustomer(Long customerId, Long restaurantId) {
        requireRestaurant(restaurantId);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        customer.setRestaurantId(restaurantId);
        customer.setTenantAssignmentConfidence(AssignmentConfidence.HIGH); // reviewed/confirmed by admin
        Customer saved = customerRepository.save(customer);

        // Move the customer's tenant-scoped child rows to the same restaurant. Otherwise they keep the
        // OLD restaurant_id and, under tenant enforcement, become invisible in the new tenant — the
        // loyalty row especially would then be re-INSERTed on next access and collide with its UNIQUE
        // customer_id (500). bonus_transactions / tier_history link via customer_loyalty_id.
        int loyalty = jdbcTemplate.update(
                "UPDATE customer_loyalty SET restaurant_id = ? WHERE customer_id = ?", restaurantId, customerId);
        int wallet = jdbcTemplate.update(
                "UPDATE wallet_top_ups SET restaurant_id = ? WHERE customer_id = ?", restaurantId, customerId);
        int bonus = jdbcTemplate.update(
                "UPDATE bonus_transactions SET restaurant_id = ? WHERE customer_loyalty_id IN "
                        + "(SELECT id FROM customer_loyalty WHERE customer_id = ?)", restaurantId, customerId);
        int tiers = jdbcTemplate.update(
                "UPDATE tier_history SET restaurant_id = ? WHERE customer_loyalty_id IN "
                        + "(SELECT id FROM customer_loyalty WHERE customer_id = ?)", restaurantId, customerId);
        log.info("Reassigned customer {} to restaurant {} (moved loyalty={}, wallet={}, bonus={}, tiers={})",
                customerId, restaurantId, loyalty, wallet, bonus, tiers);

        return saved;
    }

    @Transactional
    public Waiter reassignWaiter(Long waiterId, Long restaurantId) {
        requireRestaurant(restaurantId);
        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Waiter not found with id: " + waiterId));
        waiter.setRestaurantId(restaurantId);
        waiter.setTenantAssignmentConfidence(AssignmentConfidence.HIGH); // reviewed/confirmed by admin
        return waiterRepository.save(waiter);
    }

    private void requireRestaurant(Long restaurantId) {
        if (restaurantId == null || !restaurantRepository.existsById(restaurantId)) {
            throw new BadRequestException("Restaurant not found: " + restaurantId);
        }
    }
}
