package com.elcafe.bff.customer.service;

import com.elcafe.bff.customer.dto.CustomerOrderDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
                .placedAt(order.getCreatedAt())
                .estimatedReadyTime(order.getEstimatedReadyTime())
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime())
                .canCancel(canCancel(order))
                .canModify(canModify(order))
                .canReorder(order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.DELIVERED)
                .canRate(order.getStatus() == OrderStatus.DELIVERED && order.getRating() == null)
                .restaurantName(restaurant.getName())
                .restaurantPhone(restaurant.getPhone())
                .restaurantLogoUrl(restaurant.getLogoUrl())
                .build();
    }

    private String getStatusDisplayText(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Order Received";
            case CONFIRMED -> "Order Confirmed";
            case PREPARING -> "Preparing Your Order";
            case READY -> "Ready for Pickup";
            case OUT_FOR_DELIVERY -> "Out for Delivery";
            case DELIVERED -> "Delivered";
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
            default -> status.name();
        };
    }

    private String getStatusDescription(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Your order has been received and is waiting to be confirmed.";
            case CONFIRMED -> "The restaurant has confirmed your order and will start preparing it soon.";
            case PREPARING -> "The kitchen is preparing your delicious meal.";
            case READY -> "Your order is ready! Come pick it up or wait for your courier.";
            case OUT_FOR_DELIVERY -> "Your order is on its way to you.";
            case DELIVERED -> "Your order has been delivered. Enjoy your meal!";
            case COMPLETED -> "Thank you for your order!";
            case CANCELLED -> "This order has been cancelled.";
            default -> "";
        };
    }

    private Integer getStatusStep(OrderStatus status) {
        return switch (status) {
            case PENDING -> 1;
            case CONFIRMED -> 2;
            case PREPARING -> 3;
            case READY -> 4;
            case OUT_FOR_DELIVERY -> 4;
            case DELIVERED, COMPLETED -> 5;
            default -> 0;
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
                    .map(addOn -> addOn.getAddOn().getName())
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
        Restaurant restaurant = order.getRestaurant();
        String currency = restaurant.getCurrencyCode() != null ? restaurant.getCurrencyCode() : "USD";
        String currencySymbol = restaurant.getCurrencySymbol() != null ? restaurant.getCurrencySymbol() : "$";

        return CustomerOrderDTO.OrderPricingDTO.builder()
                .subtotal(order.getSubtotal())
                .discount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .discountDescription(order.getDiscountDescription())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .tax(order.getTax())
                .tip(order.getTip())
                .total(order.getTotal())
                .currency(currency)
                .currencySymbol(currencySymbol)
                .build();
    }

    private CustomerOrderDTO.DeliveryInfoDTO assembleDeliveryInfo(Order order) {
        if (order.getDeliveryAddress() == null) {
            return CustomerOrderDTO.DeliveryInfoDTO.builder()
                    .deliveryType(order.getOrderType() != null ? order.getOrderType().name() : "PICKUP")
                    .build();
        }

        CustomerOrderDTO.TrackingInfoDTO tracking = null;
        if (order.getCourier() != null && order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
            tracking = CustomerOrderDTO.TrackingInfoDTO.builder()
                    .courierLatitude(order.getCourier().getCurrentLatitude())
                    .courierLongitude(order.getCourier().getCurrentLongitude())
                    .restaurantLatitude(order.getRestaurant().getLatitude())
                    .restaurantLongitude(order.getRestaurant().getLongitude())
                    .customerLatitude(order.getDeliveryAddress().getLatitude())
                    .customerLongitude(order.getDeliveryAddress().getLongitude())
                    .build();
        }

        return CustomerOrderDTO.DeliveryInfoDTO.builder()
                .deliveryType(order.getOrderType() != null ? order.getOrderType().name() : "DELIVERY")
                .address(formatAddress(order))
                .instructions(order.getDeliveryAddress().getDeliveryInstructions())
                .courierName(order.getCourier() != null ? order.getCourier().getName() : null)
                .courierPhone(order.getCourier() != null ? order.getCourier().getPhone() : null)
                .tracking(tracking)
                .build();
    }

    private String formatAddress(Order order) {
        if (order.getDeliveryAddress() == null) {
            return null;
        }

        var addr = order.getDeliveryAddress();
        StringBuilder sb = new StringBuilder();
        if (addr.getStreet() != null) sb.append(addr.getStreet());
        if (addr.getApartment() != null) sb.append(", ").append(addr.getApartment());
        if (addr.getCity() != null) sb.append(", ").append(addr.getCity());
        if (addr.getState() != null) sb.append(", ").append(addr.getState());
        if (addr.getZipCode() != null) sb.append(" ").append(addr.getZipCode());
        return sb.toString();
    }

    private boolean canCancel(Order order) {
        return order.getStatus() == OrderStatus.PENDING ||
               order.getStatus() == OrderStatus.CONFIRMED;
    }

    private boolean canModify(Order order) {
        return order.getStatus() == OrderStatus.PENDING;
    }
}
