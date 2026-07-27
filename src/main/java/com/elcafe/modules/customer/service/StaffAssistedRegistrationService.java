package com.elcafe.modules.customer.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.dto.StaffRegistrationRequest;
import com.elcafe.modules.customer.dto.StaffRegistrationResponse;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * V182: the till-side half of the welcome bonus — an employee registers a walk-in guest while taking
 * payment, and the guest is credited immediately.
 *
 * <p><b>Why there is no OTP here.</b> The online door proves the phone with the consumer OTP before
 * anything is credited. This one does not, on purpose: a cashier cannot hold up a queue while a guest
 * finds their phone and reads back six digits, and an offer that slows down service is an offer staff
 * stop making. The employee standing with the guest is the trust anchor instead.
 *
 * <p>That trade means real value is credited against a number nobody confirmed, so the guard is
 * <em>visibility</em> rather than friction: every customer created here records
 * {@link Customer#getRegisteredByUserId()}, which is what makes "registrations per employee per shift"
 * answerable and the whole decision reversible if the door is ever abused. Two other things bound the
 * damage — the phone is matched before anything is created, so the same guest cannot be registered
 * twice into two records, and {@code LoyaltyService.grantRegistrationBonus} is idempotent per customer,
 * so no one can be credited a second time through either door.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffAssistedRegistrationService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final LoyaltyService loyaltyService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @Transactional
    public StaffRegistrationResponse register(StaffRegistrationRequest request, Long actingUserId) {
        Long restaurantId = restaurantAuthorizationService.currentTenantScopeStrict();
        if (restaurantId == null) {
            // A platform account has no restaurant to register a guest into. Registering "for the
            // platform" would produce a customer no tenant owns, invisible under the restaurant filter.
            throw new BadRequestException(
                    "A guest belongs to a restaurant. Sign in with a restaurant-scoped account to register one.");
        }

        String phone = normalizePhone(request.getPhone());
        if (phone.isBlank()) {
            throw new BadRequestException("Phone number is required.");
        }

        // Match before creating: the same person handed to two cashiers on two visits must stay one
        // customer, or their history splits in half and the bonus rule can be walked around.
        Optional<Customer> existing = customerRepository.findByPhoneAndRestaurantId(phone, restaurantId);
        boolean alreadyRegistered = existing.isPresent();

        Customer customer = existing.orElseGet(() -> {
            Customer fresh = Customer.builder()
                    .restaurantId(restaurantId)
                    .phone(phone)
                    .firstName(safeName(request.getFirstName(), phone))
                    .lastName(request.getLastName())
                    .registrationSource(RegistrationSource.WALK_IN)
                    .registeredByUserId(actingUserId)
                    .build();
            return customerRepository.save(fresh);
        });

        boolean orderLinked = linkOrderIfPresent(request.getOrderId(), customer, restaurantId);

        // Idempotent: an existing customer who already had the welcome bonus is credited zero, and the
        // response says so rather than letting the cashier promise something that did not happen.
        BigDecimal granted = loyaltyService.grantRegistrationBonus(customer.getId(), restaurantId);

        log.info("Staff-assisted registration by user {} — customerId={}, alreadyRegistered={}, granted={}",
                actingUserId, customer.getId(), alreadyRegistered, granted);

        return StaffRegistrationResponse.builder()
                .customerId(customer.getId())
                .customerName(displayName(customer))
                .alreadyRegistered(alreadyRegistered)
                .bonusGranted(granted)
                .orderLinked(orderLinked)
                .build();
    }

    /**
     * Attach the guest to the order being paid so this visit counts as theirs. Tenant-checked, and
     * never overwrites a customer the order already has — an order already attributed to someone else
     * is not this guest's to claim.
     */
    private boolean linkOrderIfPresent(Long orderId, Customer customer, Long restaurantId) {
        if (orderId == null) {
            return false;
        }
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null
                || order.getRestaurant() == null
                || !restaurantId.equals(order.getRestaurant().getId())) {
            // Another tenant's order id, or none at all, reads as "nothing to link" rather than an
            // error: the registration itself succeeded and is worth keeping.
            log.warn("Staff registration: order {} not linkable for restaurant {}", orderId, restaurantId);
            return false;
        }
        if (order.getCustomer() != null) {
            return false;
        }
        order.setCustomer(customer);
        orderRepository.save(order);
        return true;
    }

    /** Same normalisation the OTP door uses, so one guest resolves to one record across both. */
    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9+]", "");
    }

    private static String safeName(String firstName, String phone) {
        if (firstName != null && !firstName.isBlank()) {
            return firstName.trim();
        }
        return "Guest " + phone.substring(Math.max(0, phone.length() - 4));
    }

    private static String displayName(Customer customer) {
        String first = customer.getFirstName() == null ? "" : customer.getFirstName();
        String last = customer.getLastName() == null ? "" : customer.getLastName();
        return (first + " " + last).trim();
    }
}
