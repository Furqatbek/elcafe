package com.elcafe.modules.partner.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.AddOnGroup;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.AddOnRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.repository.ProductVariantRepository;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.OrderItemAddOn;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.partner.dto.PartnerOrderRequest;
import com.elcafe.modules.partner.dto.PartnerOrderResponse;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerOrder;
import com.elcafe.modules.partner.exception.PartnerOrderRejectedException;
import com.elcafe.modules.partner.repository.PartnerOrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Accepts orders pushed in by a delivery aggregator.
 *
 * <p>The single most important line in this class is the call to {@link OrderService#createOrder} — not
 * {@code orderRepository.save}. Printing the kitchen ticket is a side effect of that one method, so an
 * order created any other way reaches the database and the dashboards but never the printer. Going
 * through it is what makes "the receipt prints when an order arrives" true, and it must stay that way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerOrderService {

    /** Tolerance when comparing the partner's expected total with ours: currency is whole-tiyin here. */
    private static final BigDecimal PRICE_TOLERANCE = new BigDecimal("0.01");

    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AddOnRepository addOnRepository;
    private final OrderRepository orderRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    /** Channel pricing. The menu endpoint uses the same resolver, so the two cannot disagree. */
    private final PartnerPricingService partnerPricingService;

    /** Lazy to break the circular wiring OrderService ↔ the services it notifies. */
    @Lazy
    private final OrderService orderService;

    /**
     * Creates the order and its correlation row in one transaction, or returns the existing order when
     * this {@code externalOrderId} has been seen before — idempotent on
     * {@code (partner, restaurant, externalOrderId)}, so a retried push cannot cook the same lunch twice.
     */
    @Transactional
    public PartnerOrderResponse createOrderInTransaction(Partner partner, PartnerOrderRequest request) {
        Optional<PartnerOrder> existing = partnerOrderRepository
                .findByPartnerIdAndRestaurantIdAndExternalOrderId(
                        partner.getId(), request.getRestaurantId(), request.getExternalOrderId());
        if (existing.isPresent()) {
            Order order = orderRepository.findById(existing.get().getOrderId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Order", "id", existing.get().getOrderId()));
            log.info("Partner {} re-pushed external order {} — returning existing order {}",
                    partner.getSlug(), request.getExternalOrderId(), order.getOrderNumber());
            return toResponse(order, request.getExternalOrderId(), true);
        }

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant", "id", request.getRestaurantId()));

        if (!Boolean.TRUE.equals(restaurant.getActive())
                || !Boolean.TRUE.equals(restaurant.getAcceptingOrders())) {
            throw new PartnerOrderRejectedException(
                    PartnerOrderRejectedException.Reason.VENUE_NOT_ACCEPTING,
                    "This venue is not accepting orders right now",
                    Map.of("restaurantId", restaurant.getId()));
        }

        if (request.getOrderType() == OrderType.DELIVERY
                && (request.getDelivery() == null
                    || request.getDelivery().getAddress() == null
                    || request.getDelivery().getAddress().isBlank())) {
            throw new BadRequestException("A delivery address is required for DELIVERY orders");
        }

        Order order = buildOrder(restaurant, partner, request,
                partnerPricingService.resolverFor(partner.getId(), restaurant.getId()));

        // The canonical creation path: assigns the order number, sets NEW, writes the first status
        // history row, saves, PRINTS THE KITCHEN TICKET, and notifies staff.
        Order saved = orderService.createOrder(order);

        partnerOrderRepository.save(PartnerOrder.builder()
                .partnerId(partner.getId())
                .restaurantId(restaurant.getId())
                .externalOrderId(request.getExternalOrderId())
                .orderId(saved.getId())
                .build());

        log.info("Partner {} created order {} for restaurant {} (external {})",
                partner.getSlug(), saved.getOrderNumber(), restaurant.getId(), request.getExternalOrderId());
        return toResponse(saved, request.getExternalOrderId(), false);
    }

    /**
     * Re-read after a constraint violation, in a fresh transaction (the failed one is poisoned).
     *
     * <p>Returns {@code null} rather than throwing when there is no mapping, because the caller needs
     * to tell "you lost the duplicate race" from "some other constraint blew up". Reporting the second
     * as a missing order would hand a partner a 404 for what is really a 500.
     */
    @Transactional(readOnly = true)
    public PartnerOrderResponse findExistingOrder(Partner partner, PartnerOrderRequest request) {
        return partnerOrderRepository
                .findByPartnerIdAndRestaurantIdAndExternalOrderId(
                        partner.getId(), request.getRestaurantId(), request.getExternalOrderId())
                .flatMap(mapping -> orderRepository.findById(mapping.getOrderId()))
                .map(order -> toResponse(order, request.getExternalOrderId(), true))
                .orElse(null);
    }

    /** The partner's own view of an order it sent us. Scoped to that partner: it sees only its own. */
    @Transactional(readOnly = true)
    public PartnerOrderResponse getOrder(Long partnerId, Long restaurantId, String externalOrderId) {
        PartnerOrder mapping = partnerOrderRepository
                .findByPartnerIdAndRestaurantIdAndExternalOrderId(partnerId, restaurantId, externalOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "externalOrderId", externalOrderId));

        Order order = orderRepository.findById(mapping.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", mapping.getOrderId()));

        return toResponse(order, externalOrderId, false);
    }

    // ------------------------------------------------------------------ order construction

    private Order buildOrder(Restaurant restaurant, Partner partner, PartnerOrderRequest request,
                             PartnerPriceResolver pricing) {
        Order order = Order.builder()
                .restaurant(restaurant)
                // No Customer. The person belongs to the partner's business, and quietly folding them
                // into our customer table would attach our loyalty and marketing to someone who never
                // agreed to either.
                .customer(null)
                .orderType(request.getOrderType())
                .orderSource(OrderSource.AGGREGATOR)
                .customerNotes(buildNotes(partner, request))
                .scheduledFor(request.getScheduledFor() != null
                        ? request.getScheduledFor().atOffset(ZoneOffset.UTC) : null)
                .items(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        // Every unknown or unavailable id is collected across the WHOLE basket before throwing. Failing
        // on the first one makes a partner fix their catalogue one item per round-trip.
        Set<Long> unknownProducts = new LinkedHashSet<>();
        Set<Long> unknownVariants = new LinkedHashSet<>();
        Set<Long> unknownAddOns = new LinkedHashSet<>();
        Set<Long> missingVariants = new LinkedHashSet<>();
        Set<Long> unavailableProducts = new LinkedHashSet<>();
        Set<Long> unavailableVariants = new LinkedHashSet<>();
        Set<Long> unavailableAddOns = new LinkedHashSet<>();

        List<OrderItem> lines = new ArrayList<>();

        for (PartnerOrderRequest.Item item : request.getItems()) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            // Cross-venue guard: a valid id from ANOTHER restaurant must read as unknown here, not as a
            // purchasable item, or a partner could order restaurant B's menu from restaurant A.
            // Unknown covers three things that must all read the same to a partner: no such product, a
            // product belonging to ANOTHER restaurant (or a valid id would let them order venue B's menu
            // from venue A), and anything not LIVE — a DRAFT or archived item is not on the menu they
            // were served, so ordering one by id must not work either.
            if (product == null || product.getCategory() == null
                    || product.getCategory().getRestaurant() == null
                    || !restaurant.getId().equals(product.getCategory().getRestaurant().getId())
                    || product.getStatus() != ProductStatus.LIVE) {
                unknownProducts.add(item.getProductId());
                continue;
            }
            // Both halves of availability: switched off by a person, or short an ingredient. The
            // second matters most on the last order before the walk-in runs dry — the partner's menu
            // was correct when they rendered it and is wrong by the time this arrives, and refusing
            // here is far cheaper than accepting money for a dish the kitchen then cannot make.
            if (!product.isOrderable()) {
                unavailableProducts.add(product.getId());
            }

            ProductVariant variant = null;
            if (item.getVariantId() != null) {
                variant = productVariantRepository.findById(item.getVariantId()).orElse(null);
                if (variant == null || variant.getProduct() == null
                        || !product.getId().equals(variant.getProduct().getId())) {
                    unknownVariants.add(item.getVariantId());
                    continue;
                }
                if (!Boolean.TRUE.equals(variant.getInStock())
                        || !Boolean.TRUE.equals(variant.getIsAvailable())) {
                    unavailableVariants.add(variant.getId());
                }
            } else if (Boolean.TRUE.equals(product.getHasVariants())) {
                // Falling back to the base price here is how a venue gets underpaid: a large pizza
                // charged at the leftover base price, cooked large, with nothing on the ticket to show
                // the size. expectedTotal would not catch it either, because the partner quoted from
                // the same base price. Refuse instead and make them name the variant.
                missingVariants.add(product.getId());
                continue;
            }

            // Channel price, not base price — the number this partner was shown in the menu.
            BigDecimal unitPrice = variant != null
                    ? pricing.forVariant(variant, product)
                    : pricing.forProduct(product);

            List<OrderItemAddOn> itemAddOns = new ArrayList<>();
            BigDecimal addOnTotal = BigDecimal.ZERO;
            for (Long addOnId : safeList(item.getAddOnIds())) {
                AddOn addOn = addOnRepository.findById(addOnId).orElse(null);
                if (addOn == null || !belongsToProduct(addOn, product)) {
                    unknownAddOns.add(addOnId);
                    continue;
                }
                if (!Boolean.TRUE.equals(addOn.getAvailable())) {
                    unavailableAddOns.add(addOnId);
                }
                BigDecimal addOnPrice = pricing.forAddOn(addOn);
                addOnTotal = addOnTotal.add(addOnPrice);
                itemAddOns.add(OrderItemAddOn.builder()
                        .addOnId(addOn.getId())
                        .addOnName(addOn.getName())
                        .addOnPrice(addOnPrice)
                        // Scaled with the line, not fixed at 1. The add-on is priced per unit (it is
                        // multiplied into lineUnitPrice below), so two lattes with an extra shot are
                        // two extra shots — and the ticket has to say so, or the barista pulls one.
                        .quantity(item.getQuantity())
                        .build());
            }

            BigDecimal lineUnitPrice = unitPrice.add(addOnTotal);
            BigDecimal lineTotal = lineUnitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .variantId(variant != null ? variant.getId() : null)
                    .variantName(variant != null ? variant.getName() : null)
                    .quantity(item.getQuantity())
                    .unitPrice(lineUnitPrice)
                    .totalPrice(lineTotal)
                    .specialInstructions(item.getSpecialInstructions())
                    .build();
            itemAddOns.forEach(addOn -> addOn.setOrderItem(orderItem));
            orderItem.setItemAddOns(itemAddOns);

            lines.add(orderItem);
            subtotal = subtotal.add(lineTotal);
        }

        rejectIfAnyUnknown(unknownProducts, unknownVariants, unknownAddOns);
        rejectIfMissingVariant(missingVariants);
        rejectIfAnyUnavailable(unavailableProducts, unavailableVariants, unavailableAddOns);

        lines.forEach(order::addItem);

        // Only a delivery carries a delivery fee; a courier collecting for pickup is the partner's cost,
        // not the customer's second one.
        BigDecimal deliveryFee = request.getOrderType() == OrderType.DELIVERY
                && restaurant.getDeliveryFee() != null
                ? restaurant.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee);

        verifyExpectedTotal(request, subtotal, deliveryFee, total);

        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTax(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(total);

        if (request.getOrderType() == OrderType.DELIVERY) {
            PartnerOrderRequest.Delivery delivery = request.getDelivery();
            order.setDeliveryInfo(DeliveryInfo.builder()
                    .order(order)
                    .address(delivery.getAddress())
                    .latitude(delivery.getLatitude() != null ? delivery.getLatitude().doubleValue() : null)
                    .longitude(delivery.getLongitude() != null ? delivery.getLongitude().doubleValue() : null)
                    .contactName(request.getCustomer() != null ? request.getCustomer().getName() : null)
                    .contactPhone(request.getCustomer() != null ? request.getCustomer().getPhone() : null)
                    .deliveryInstructions(delivery.getInstructions())
                    .build());
        }

        order.setPayment(buildPayment(order, partner, request, total));
        return order;
    }

    /**
     * PREPAID arrives settled — the partner already took the customer's money, so leaving it PENDING
     * would show the venue a debt that will never be collected and corrupt the day's takings.
     */
    private Payment buildPayment(Order order, Partner partner, PartnerOrderRequest request, BigDecimal total) {
        boolean prepaid = request.getPaymentMode() == PartnerOrderRequest.PaymentMode.PREPAID;
        Payment payment = Payment.builder()
                .order(order)
                .method(prepaid ? PaymentMethod.ONLINE : PaymentMethod.CASH)
                .status(prepaid ? PaymentStatus.COMPLETED : PaymentStatus.PENDING)
                .amount(total)
                .build();
        if (prepaid) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            payment.setPaidAt(now);
            payment.setCompletedAt(now);
            // Names who holds the money, so reconciliation can tell a partner's prepaid order from
            // one of our own online payments.
            payment.setPaymentGateway(partner.getSlug());
            payment.setTransactionId(request.getExternalOrderId());
        }
        return payment;
    }

    /**
     * Leads the ticket with the partner and their order id. The counter's first question about an
     * aggregator order is "whose is this and what do I quote them", and the printed ticket is often the
     * only thing in front of them.
     */
    private String buildNotes(Partner partner, PartnerOrderRequest request) {
        StringBuilder notes = new StringBuilder()
                .append('[').append(partner.getName()).append(" #").append(request.getExternalOrderId())
                .append(']');

        PartnerOrderRequest.Customer customer = request.getCustomer();
        if (customer != null && (customer.getName() != null || customer.getPhone() != null)) {
            notes.append(' ');
            if (customer.getName() != null) {
                notes.append(customer.getName());
            }
            if (customer.getPhone() != null) {
                notes.append(customer.getName() != null ? ", " : "").append(customer.getPhone());
            }
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            notes.append(" — ").append(request.getNotes());
        }

        String result = notes.toString();
        // customerNotes is a 1000-char column and the partner controls part of this string.
        return result.length() > 1000 ? result.substring(0, 1000) : result;
    }

    private void verifyExpectedTotal(PartnerOrderRequest request, BigDecimal subtotal,
                                     BigDecimal deliveryFee, BigDecimal total) {
        if (request.getExpectedTotal() == null) {
            return;
        }
        if (request.getExpectedTotal().subtract(total).abs().compareTo(PRICE_TOLERANCE) > 0) {
            // Refuse rather than silently reprice. The customer has already been charged the partner's
            // number; cooking at ours turns a stale menu into a reconciliation dispute per order.
            Map<String, Object> details = new java.util.LinkedHashMap<>();
            details.put("expectedTotal", request.getExpectedTotal());
            details.put("actualTotal", total);
            details.put("subtotal", subtotal);
            details.put("deliveryFee", deliveryFee);

            // The one wrong answer we can recognise on sight, and it is not a stale menu at all: a
            // number matching our goods exactly means the delivery fee was left out of the sum. The
            // first partner to integrate read it that way, so the next one will too — and without
            // this the symptom is every delivery order refused for "a price mismatch" while the
            // prices are in fact identical.
            if (deliveryFee.compareTo(BigDecimal.ZERO) > 0
                    && request.getExpectedTotal().subtract(subtotal).abs()
                            .compareTo(PRICE_TOLERANCE) <= 0) {
                details.put("hint", "Your total matches our goods subtotal exactly, so the prices "
                        + "agree — expectedTotal must also include deliveryFee.");
            }

            throw new PartnerOrderRejectedException(
                    PartnerOrderRejectedException.Reason.PRICE_MISMATCH,
                    "Order total does not match ours — re-pull the menu and retry",
                    details);
        }
    }

    private void rejectIfAnyUnknown(Set<Long> products, Set<Long> variants, Set<Long> addOns) {
        if (products.isEmpty() && variants.isEmpty() && addOns.isEmpty()) {
            return;
        }
        throw new PartnerOrderRejectedException(
                PartnerOrderRejectedException.Reason.UNKNOWN_ITEMS,
                "Some items are not on this venue's menu",
                Map.of("unknownProductIds", List.copyOf(products),
                        "unknownVariantIds", List.copyOf(variants),
                        "unknownAddOnIds", List.copyOf(addOns)));
    }

    private void rejectIfMissingVariant(Set<Long> products) {
        if (products.isEmpty()) {
            return;
        }
        throw new PartnerOrderRejectedException(
                PartnerOrderRejectedException.Reason.VARIANT_REQUIRED,
                "These products are sold by variant — send the variantId",
                Map.of("variantRequiredProductIds", List.copyOf(products)));
    }

    private void rejectIfAnyUnavailable(Set<Long> products, Set<Long> variants, Set<Long> addOns) {
        if (products.isEmpty() && variants.isEmpty() && addOns.isEmpty()) {
            return;
        }
        throw new PartnerOrderRejectedException(
                PartnerOrderRejectedException.Reason.ITEMS_UNAVAILABLE,
                "Some items are unavailable right now",
                Map.of("unavailableProductIds", List.copyOf(products),
                        "unavailableVariantIds", List.copyOf(variants),
                        "unavailableAddOnIds", List.copyOf(addOns)));
    }

    /** An add-on counts only if it hangs off one of this product's own add-on groups. */
    private boolean belongsToProduct(AddOn addOn, Product product) {
        if (addOn.getAddOnGroup() == null) {
            return false;
        }
        Long groupId = addOn.getAddOnGroup().getId();
        return product.getAddOnGroups().stream()
                .map(AddOnGroup::getId)
                .anyMatch(groupId::equals);
    }

    private List<Long> safeList(List<Long> values) {
        return values == null ? List.of() : values;
    }

    private PartnerOrderResponse toResponse(Order order, String externalOrderId, boolean duplicate) {
        List<PartnerOrderResponse.Line> lines = order.getItems() == null ? List.of()
                : order.getItems().stream()
                        .map(item -> PartnerOrderResponse.Line.builder()
                                .productId(item.getProductId())
                                .productName(item.getProductName())
                                .variantId(item.getVariantId())
                                .variantName(item.getVariantName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .totalPrice(item.getTotalPrice())
                                .build())
                        .toList();

        return PartnerOrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .externalOrderId(externalOrderId)
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .createdAt(order.getCreatedAt())
                .duplicate(duplicate)
                .items(lines)
                .build();
    }
}
