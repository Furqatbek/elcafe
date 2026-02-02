package com.elcafe.bff.customer.service;

import com.elcafe.bff.customer.dto.CustomerOrderDTO;
import com.elcafe.modules.order.entity.DeliveryInfo;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BFF Assembler for Customer Orders.
 * Transforms internal order structure to customer-friendly format.
 */
@Service
@RequiredArgsConstructor
public class CustomerOrderAssembler {

    public CustomerOrderDTO assembleOrder(Order order) {
        Restaurant restaurant = order.getRestaurant();

        return CustomerOrderDTO.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .statusDisplayText(getStatusDisplayText(order.getStatus()))
                .statusDescription(getStatusDescription(order.getStatus()))
                .statusStep(getStatusStep(order.getStatus()))
                .items(assembleItems(order))
                .pricing(assemblePricing(order))
                .delivery(assembleDeliveryInfo(order))
                .placedAt(order.getCreatedAt() != null ? order.getCreatedAt().toLocalDateTime() : null)
                .estimatedReadyTime(null)  // Field doesn't exist on Order
                .estimatedDeliveryTime(getEstimatedDeliveryTime(order))
                .canCancel(canCancel(order))
                .canModify(canModify(order))
                .canReorder(order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED)
                .canRate(order.getStatus() == OrderStatus.DELIVERED)  // Simplified - rating field doesn't exist
                .restaurantName(restaurant.getName())
                .restaurantPhone(restaurant.getPhone())
                .restaurantLogoUrl(restaurant.getLogoUrl())
                .build();
    }

    private LocalDateTime getEstimatedDeliveryTime(Order order) {
        if (order.getDeliveryInfo() != null && order.getDeliveryInfo().getEstimatedDeliveryTime() != null) {
            return order.getDeliveryInfo().getEstimatedDeliveryTime();
        }
        return null;
    }

    private String getStatusDisplayText(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Order Received";
            case NEW -> "Order Received";
            case PLACED -> "Order Placed";
            case ACCEPTED -> "Order Confirmed";
            case REJECTED -> "Order Rejected";
            case PREPARING -> "Preparing Your Order";
            case READY -> "Ready for Pickup";
            case PICKED_UP -> "Picked Up";
            case COURIER_ASSIGNED -> "Courier Assigned";
            case ON_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    private String getStatusDescription(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Your order has been received and is waiting to be confirmed.";
            case NEW -> "Your order has been received and is waiting to be confirmed.";
            case PLACED -> "Your order has been placed and is waiting for restaurant confirmation.";
            case ACCEPTED -> "The restaurant has confirmed your order and will start preparing it soon.";
            case REJECTED -> "Unfortunately, the restaurant could not accept your order.";
            case PREPARING -> "The kitchen is preparing your delicious meal.";
            case READY -> "Your order is ready! Come pick it up or wait for your courier.";
            case PICKED_UP -> "Your order has been picked up.";
            case COURIER_ASSIGNED -> "A courier has been assigned to your order.";
            case ON_DELIVERY -> "Your order is on its way to you.";
            case DELIVERED -> "Your order has been delivered. Enjoy your meal!";
            case COMPLETED -> "Thank you for your order!";
            case CANCELLED -> "This order has been cancelled.";
        };
    }

    private Integer getStatusStep(OrderStatus status) {
        return switch (status) {
            case PENDING, NEW -> 1;
            case PLACED, ACCEPTED -> 2;
            case PREPARING -> 3;
            case READY, PICKED_UP, COURIER_ASSIGNED -> 4;
            case ON_DELIVERY -> 4;
            case DELIVERED, COMPLETED -> 5;
            case REJECTED, CANCELLED -> 0;
        };
    }

    private List<CustomerOrderDTO.OrderItemDTO> assembleItems(Order order) {
        if (order.getItems() == null) {
            return List.of();
        }

        return order.getItems().stream()
                .map(this::mapToOrderItem)
                .collect(Collectors.toList());
    }

    private CustomerOrderDTO.OrderItemDTO mapToOrderItem(OrderItem item) {
        List<String> addOns = List.of();
        if (item.getAddOns() != null) {
            addOns = item.getAddOns().stream()
                    .map(addOn -> addOn.getAddOn() != null ? addOn.getAddOn().getName() : "")
                    .filter(name -> !name.isEmpty())
                    .collect(Collectors.toList());
        }

        String variantName = item.getVariant() != null ? item.getVariant().getName() : null;
        String imageUrl = item.getProduct() != null ? item.getProduct().getImageUrl() : null;

        return CustomerOrderDTO.OrderItemDTO.builder()
                .productName(item.getProductName())
                .variantName(variantName)
                .quantity(item.getQuantity())
                .price(item.getTotalPrice())
                .imageUrl(imageUrl)
                .addOns(addOns)
                .specialInstructions(item.getNotes())
                .build();
    }

    private CustomerOrderDTO.OrderPricingDTO assemblePricing(Order order) {
        // Currency fields don't exist on Restaurant, use defaults
        String currency = "USD";
        String currencySymbol = "$";

        return CustomerOrderDTO.OrderPricingDTO.builder()
                .subtotal(order.getSubtotal())
                .discount(order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO)
                .discountDescription(order.getDiscountReason())  // Use discountReason instead
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .tax(order.getTax())
                .tip(order.getTipAmount())  // Use tipAmount instead of tip
                .total(order.getTotal())
                .currency(currency)
                .currencySymbol(currencySymbol)
                .build();
    }

    private CustomerOrderDTO.DeliveryInfoDTO assembleDeliveryInfo(Order order) {
        DeliveryInfo deliveryInfo = order.getDeliveryInfo();

        if (deliveryInfo == null) {
            return CustomerOrderDTO.DeliveryInfoDTO.builder()
                    .deliveryType(order.getOrderType() != null ? order.getOrderType().name() : "PICKUP")
                    .build();
        }

        CustomerOrderDTO.TrackingInfoDTO tracking = null;
        // Simplified tracking - no separate courier entity
        if (order.getStatus() == OrderStatus.ON_DELIVERY && deliveryInfo.getLatitude() != null) {
            tracking = CustomerOrderDTO.TrackingInfoDTO.builder()
                    .courierLatitude(null)  // Courier location not tracked on DeliveryInfo
                    .courierLongitude(null)
                    .restaurantLatitude(order.getRestaurant().getLatitude())
                    .restaurantLongitude(order.getRestaurant().getLongitude())
                    .customerLatitude(deliveryInfo.getLatitude())
                    .customerLongitude(deliveryInfo.getLongitude())
                    .build();
        }

        return CustomerOrderDTO.DeliveryInfoDTO.builder()
                .deliveryType(order.getOrderType() != null ? order.getOrderType().name() : "DELIVERY")
                .address(formatAddress(deliveryInfo))
                .instructions(deliveryInfo.getDeliveryInstructions())
                .courierName(deliveryInfo.getCourierName())
                .courierPhone(deliveryInfo.getCourierPhone())
                .tracking(tracking)
                .build();
    }

    private String formatAddress(DeliveryInfo deliveryInfo) {
        if (deliveryInfo == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (deliveryInfo.getAddress() != null) sb.append(deliveryInfo.getAddress());
        if (deliveryInfo.getCity() != null) sb.append(", ").append(deliveryInfo.getCity());
        if (deliveryInfo.getState() != null) sb.append(", ").append(deliveryInfo.getState());
        if (deliveryInfo.getZipCode() != null) sb.append(" ").append(deliveryInfo.getZipCode());
        return sb.toString();
    }

    private boolean canCancel(Order order) {
        return order.getStatus() == OrderStatus.PENDING ||
               order.getStatus() == OrderStatus.NEW ||
               order.getStatus() == OrderStatus.PLACED ||
               order.getStatus() == OrderStatus.ACCEPTED;
    }

    private boolean canModify(Order order) {
        return order.getStatus() == OrderStatus.PENDING ||
               order.getStatus() == OrderStatus.NEW;
    }
}
