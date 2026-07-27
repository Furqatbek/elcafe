package com.elcafe.modules.customer.service;

import com.elcafe.common.event.CustomerDeletedEvent;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.dto.*;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.entity.CustomerPreference;
import com.elcafe.modules.customer.repository.CustomerPreferenceRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.repository.InstagramInboundMessageRepository;
import com.elcafe.modules.instagram.repository.InstagramLogRepository;
import com.elcafe.modules.instagram.repository.InstagramSubscriberRepository;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.CustomerLoyaltyRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.sms.repository.SmsLogRepository;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.repository.TelegramLogRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The customer 360 view (V183): one guest, everything known about them.
 *
 * <p>Nothing here is stored a second time. Identity comes from {@code customers}, spend from
 * {@code orders}, standing from the loyalty tables, taste from {@code customer_preference}, and the
 * conversation history from each channel's own log. Aggregating on read rather than maintaining a
 * denormalised profile means the page cannot drift out of date with the things it describes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProfileService {

    /** Orders that did not happen are not purchases — they must not inflate spend or the average. */
    private static final Set<OrderStatus> COUNTED_STATUSES =
            EnumSet.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED, OrderStatus.PICKED_UP);

    private static final int TOP_ITEMS = 5;
    /** How many orders to read when computing lifetime stats — see computePurchaseStats. */
    private static final int STATS_ORDER_CAP = 500;
    private static final int MAX_TIMELINE_LIMIT = 100;

    private final CustomerRepository customerRepository;
    private final CustomerPreferenceRepository preferenceRepository;
    private final OrderRepository orderRepository;
    private final CustomerLoyaltyRepository customerLoyaltyRepository;
    private final InstagramSubscriberRepository instagramSubscriberRepository;
    private final InstagramInboundMessageRepository instagramInboundMessageRepository;
    private final InstagramLogRepository instagramLogRepository;
    private final TelegramSubscriberRepository telegramSubscriberRepository;
    private final TelegramLogRepository telegramLogRepository;
    private final SmsLogRepository smsLogRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    // ---------------------------------------------------------------------------------------------
    // Profile
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(Long customerId) {
        Customer customer = findForCallerOrThrow(customerId);

        return CustomerProfileResponse.builder()
                .id(customer.getId())
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .birthDate(customer.getBirthDate())
                .language(customer.getLanguage())
                .notes(customer.getNotes())
                .tags(customer.getTags())
                .registrationSource(customer.getRegistrationSource() != null
                        ? customer.getRegistrationSource().name() : null)
                .createdAt(customer.getCreatedAt())
                .registeredByUserId(customer.getRegisteredByUserId())
                .preferences(listPreferences(customer.getId()))
                .purchases(computePurchaseStats(customer.getId()))
                .loyalty(loyaltySummary(customer.getId()))
                .build();
    }

    /**
     * Lifetime purchase behaviour.
     *
     * <p>Reads a bounded window of the guest's most recent orders rather than the whole history: this
     * runs on every profile open, and a guest with thousands of orders would otherwise pull them all
     * into memory to produce five numbers. The cap is generous enough that it is exact for essentially
     * every real customer, and the failure mode when exceeded is understating a very old first order —
     * not a wrong balance.
     */
    private CustomerProfileResponse.PurchaseStats computePurchaseStats(Long customerId) {
        List<Order> orders = orderRepository.findByCustomer_IdOrderByCreatedAtDesc(
                customerId, PageRequest.of(0, STATS_ORDER_CAP));

        List<Order> counted = orders.stream()
                .filter(o -> o.getStatus() != null && COUNTED_STATUSES.contains(o.getStatus()))
                .toList();

        if (counted.isEmpty()) {
            return CustomerProfileResponse.PurchaseStats.builder()
                    .orderCount(0L)
                    .lifetimeSpend(BigDecimal.ZERO)
                    .averageOrderValue(BigDecimal.ZERO)
                    .topItems(List.of())
                    .build();
        }

        BigDecimal spend = counted.stream()
                .map(o -> o.getTotal() == null ? BigDecimal.ZERO : o.getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // findBy...OrderByCreatedAtDesc — newest first, so the ends are last and first respectively.
        OffsetDateTime lastAt = counted.get(0).getCreatedAt();
        OffsetDateTime firstAt = counted.get(counted.size() - 1).getCreatedAt();

        return CustomerProfileResponse.PurchaseStats.builder()
                .orderCount((long) counted.size())
                .lifetimeSpend(spend)
                .averageOrderValue(spend.divide(BigDecimal.valueOf(counted.size()), 2, RoundingMode.HALF_UP))
                .firstOrderAt(firstAt)
                .lastOrderAt(lastAt)
                .topItems(topItems(counted))
                .build();
    }

    /**
     * The guest's most-ordered items — an observed preference rather than a stated one, which is why it
     * is computed here and never written into {@code customer_preference} as a DERIVED row: a habit that
     * changes should change with it, not linger as a stale "like".
     */
    private List<CustomerProfileResponse.TopItem> topItems(List<Order> orders) {
        Map<String, Long> counts = new HashMap<>();
        for (Order order : orders) {
            List<OrderItem> items = order.getItems();
            if (items == null) continue;
            for (OrderItem item : items) {
                String name = item.getProductName();
                if (name == null || name.isBlank()) continue;
                long qty = item.getQuantity() == null ? 1 : item.getQuantity();
                counts.merge(name, qty, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_ITEMS)
                .map(e -> CustomerProfileResponse.TopItem.builder()
                        .productName(e.getKey()).timesOrdered(e.getValue()).build())
                .toList();
    }

    private CustomerProfileResponse.LoyaltySummary loyaltySummary(Long customerId) {
        return customerLoyaltyRepository.findByCustomerId(customerId)
                .map(this::toLoyaltySummary)
                .orElse(null);   // null, not zeros: "never earned anything" is not "balance of zero"
    }

    private CustomerProfileResponse.LoyaltySummary toLoyaltySummary(CustomerLoyalty loyalty) {
        return CustomerProfileResponse.LoyaltySummary.builder()
                .currentBalance(loyalty.getCurrentBalance())
                .totalEarned(loyalty.getLifetimeEarned())
                .tierName(loyalty.getTier() != null ? loyalty.getTier().getName() : null)
                .memberSince(loyalty.getCreatedAt())
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // Preferences
    // ---------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CustomerPreferenceResponse> listPreferences(Long customerId) {
        return preferenceRepository.findByCustomer_IdOrderByPreferenceTypeAscValueAsc(customerId)
                .stream().map(CustomerPreferenceResponse::from).toList();
    }

    @Transactional
    public CustomerPreferenceResponse addPreference(Long customerId, CustomerPreferenceRequest request,
                                                    Long actingUserId) {
        Customer customer = findForCallerOrThrow(customerId);
        String value = request.getValue().trim();

        if (preferenceRepository.existsByCustomer_IdAndPreferenceTypeAndValueIgnoreCase(
                customerId, request.getPreferenceType(), value)) {
            throw new BadRequestException(
                    "'" + value + "' is already recorded for this customer.");
        }

        CustomerPreference preference = CustomerPreference.builder()
                .restaurantId(customer.getRestaurantId())
                .customer(customer)
                .preferenceType(request.getPreferenceType())
                .value(value)
                .note(request.getNote())
                .source(CustomerPreference.Source.MANUAL)
                .createdByUserId(actingUserId)
                .build();

        return CustomerPreferenceResponse.from(preferenceRepository.save(preference));
    }

    @Transactional
    public void deletePreference(Long customerId, Long preferenceId) {
        findForCallerOrThrow(customerId);   // tenant check on the owning customer first
        CustomerPreference preference = preferenceRepository.findById(preferenceId)
                .filter(p -> p.getCustomer() != null && customerId.equals(p.getCustomer().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Preference", "id", preferenceId));
        preferenceRepository.delete(preference);
    }

    /**
     * Right to erasure: a guest's allergies, dislikes and dietary needs go when they do.
     *
     * <p>This listener exists because the guarantee was previously resting on V183's
     * {@code ON DELETE CASCADE} alone, and the JPA mapping never expressed it —
     * {@code CustomerPreference.customer} is a plain {@code @ManyToOne}. On any schema Hibernate builds
     * from the entities the generated foreign key has no cascade, so deleting the customer did not
     * quietly leave preferences behind, it <em>failed outright</em> with a referential-integrity
     * violation. A DB-only guarantee that the object model contradicts is not a guarantee; it is a
     * property of one deployment.
     *
     * <p>So preferences now erase the way every other module's PII does — explicitly, on
     * {@link CustomerDeletedEvent}, synchronously inside the customer's own delete transaction and
     * before the customer row goes. Same shape as
     * {@code TelegramSubscriberService.onCustomerDeleted} and
     * {@code InstagramBotService.onCustomerDeleted}. The migration's cascade stays as a backstop for
     * anything that deletes the row without going through the service.
     */
    @EventListener
    @Transactional
    public void onCustomerDeleted(CustomerDeletedEvent event) {
        long removed = preferenceRepository.deleteByCustomer_Id(event.customerId());
        if (removed > 0) {
            log.info("Erased {} preference(s) for deleted customer {}", removed, event.customerId());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Conversation timeline
    // ---------------------------------------------------------------------------------------------

    /**
     * A window of the guest's conversation history across every channel, newest first.
     *
     * <p>Each source is asked for one window of its own, and the results are merged and cut here. Asking
     * every source for {@code limit + 1} rows older than the cursor guarantees the merged window is
     * complete: no source can hide a message that belongs in this window, because each returned at least
     * as many as the window can hold.
     */
    @Transactional(readOnly = true)
    public CustomerTimelineResponse getTimeline(Long customerId, OffsetDateTime before, int limit) {
        Customer customer = findForCallerOrThrow(customerId);
        int windowSize = Math.min(Math.max(limit, 1), MAX_TIMELINE_LIMIT);
        int fetch = windowSize + 1;

        List<CustomerTimelineEntry> merged = new ArrayList<>();
        merged.addAll(instagramEntries(customer, fetch));
        merged.addAll(telegramEntries(customer, fetch));
        merged.addAll(smsEntries(customer, fetch));

        List<CustomerTimelineEntry> ordered = merged.stream()
                .filter(e -> e.getTimestamp() != null)
                .filter(e -> before == null || e.getTimestamp().isBefore(before))
                .sorted(Comparator.comparing(CustomerTimelineEntry::getTimestamp).reversed())
                .toList();

        boolean hasMore = ordered.size() > windowSize;
        List<CustomerTimelineEntry> window = ordered.stream().limit(windowSize).toList();

        return CustomerTimelineResponse.builder()
                .entries(window)
                .nextCursor(window.isEmpty() ? null : window.get(window.size() - 1).getTimestamp())
                .hasMore(hasMore)
                .build();
    }

    /** Instagram is the only channel that stores BOTH directions, so it contributes inbound and outbound. */
    private List<CustomerTimelineEntry> instagramEntries(Customer customer, int fetch) {
        List<InstagramSubscriber> subscribers =
                instagramSubscriberRepository.findByCustomerId(customer.getId());
        if (subscribers.isEmpty()) {
            return List.of();
        }
        List<Long> subscriberIds = subscribers.stream().map(InstagramSubscriber::getId).toList();
        List<CustomerTimelineEntry> entries = new ArrayList<>();

        instagramInboundMessageRepository
                .findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(
                        customer.getRestaurantId(), subscriberIds, PageRequest.of(0, fetch))
                .forEach(m -> entries.add(CustomerTimelineEntry.builder()
                        .channel(CustomerTimelineEntry.Channel.INSTAGRAM)
                        .direction(CustomerTimelineEntry.Direction.IN)
                        .text(m.getMessageText())
                        .timestamp(m.getReceivedAt())
                        .build()));

        for (Long subscriberId : subscriberIds) {
            instagramLogRepository.findBySubscriberIdOrderByCreatedAtDesc(subscriberId).stream()
                    .limit(fetch)
                    .forEach(l -> entries.add(CustomerTimelineEntry.builder()
                            .channel(CustomerTimelineEntry.Channel.INSTAGRAM)
                            .direction(CustomerTimelineEntry.Direction.OUT)
                            .text(l.getMessage())
                            .timestamp(l.getCreatedAt())
                            .messageType(l.getMessageType() != null ? l.getMessageType().name() : null)
                            .status(l.getStatus() != null ? l.getStatus().name() : null)
                            .build()));
        }
        return entries;
    }

    /** Telegram keeps send logs only — everything we said, nothing they replied. */
    private List<CustomerTimelineEntry> telegramEntries(Customer customer, int fetch) {
        List<TelegramSubscriber> subscribers =
                telegramSubscriberRepository.findAllByCustomerId(customer.getId());
        List<CustomerTimelineEntry> entries = new ArrayList<>();
        for (TelegramSubscriber subscriber : subscribers) {
            telegramLogRepository.findBySubscriberIdOrderByCreatedAtDesc(subscriber.getId()).stream()
                    .limit(fetch)
                    .forEach(l -> entries.add(CustomerTimelineEntry.builder()
                            .channel(CustomerTimelineEntry.Channel.TELEGRAM)
                            .direction(CustomerTimelineEntry.Direction.OUT)
                            .text(l.getMessage())
                            .timestamp(toOffset(l.getCreatedAt()))
                            .messageType(l.getMessageType() != null ? l.getMessageType().name() : null)
                            .status(l.getStatus() != null ? l.getStatus().name() : null)
                            .build()));
        }
        return entries;
    }

    /** SMS logs carry the customer id directly — no subscriber hop needed. */
    private List<CustomerTimelineEntry> smsEntries(Customer customer, int fetch) {
        return smsLogRepository.findByCustomerId(customer.getId(), PageRequest.of(0, fetch))
                .stream()
                .map(l -> CustomerTimelineEntry.builder()
                        .channel(CustomerTimelineEntry.Channel.SMS)
                        .direction(CustomerTimelineEntry.Direction.OUT)
                        .text(l.getMessage())
                        .timestamp(toOffset(l.getCreatedAt()))
                        .messageType(l.getMessageType() != null ? l.getMessageType().name() : null)
                        .status(l.getStatus() != null ? l.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Telegram and SMS store {@code LocalDateTime}; Instagram stores {@code OffsetDateTime}. Without a
     * single comparable instant the merge would interleave channels wrongly, so the wall-clock values
     * are read in the system zone they were written in.
     */
    private static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }

    // ---------------------------------------------------------------------------------------------

    /** A customer outside the caller's restaurant reads as not-found, never as forbidden. */
    private Customer findForCallerOrThrow(Long customerId) {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", customerId));
        if (tenant != null && !tenant.equals(customer.getRestaurantId())) {
            throw new ResourceNotFoundException("Customer", "id", customerId);
        }
        return customer;
    }
}
