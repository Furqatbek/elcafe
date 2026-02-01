package com.elcafe.modules.order.service;

import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.pos.ModifyOrderItemRequest;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for managing order items.
 * Handles adding, removing, and updating items within an order.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class POSOrderItemService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    /**
     * Add item to an existing order
     */
    @Transactional
    public Order addItemToOrder(Long orderId, ModifyOrderItemRequest request) {
        log.info("Adding item to order: {} product: {}", orderId, request.getProductId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        validateOrderCanBeModified(order);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + request.getProductId()));

        // Check inventory availability
        List<String> missingIngredients = inventoryService.getMissingIngredients(
                request.getProductId(), request.getQuantity());
        if (!missingIngredients.isEmpty()) {
            throw new IllegalStateException("Insufficient inventory: " + String.join("; ", missingIngredients));
        }

        OrderItem newItem = createOrderItem(order, product, request);
        order.addItem(newItem);

        recalculateOrderTotals(order);

        Order savedOrder = orderRepository.save(order);
        log.info("Item added to order: {}", orderId);

        return savedOrder;
    }

    /**
     * Remove item from an existing order
     */
    @Transactional
    public Order removeItemFromOrder(Long orderId, Long itemId) {
        log.info("Removing item {} from order: {}", itemId, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        validateOrderCanBeModified(order);

        OrderItem itemToRemove = order.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found with ID: " + itemId));

        order.getItems().remove(itemToRemove);

        recalculateOrderTotals(order);

        Order savedOrder = orderRepository.save(order);
        log.info("Item {} removed from order: {}", itemId, orderId);

        return savedOrder;
    }

    /**
     * Update item quantity in an existing order
     */
    @Transactional
    public Order updateItemQuantity(Long orderId, Long itemId, Integer newQuantity) {
        log.info("Updating item {} quantity to {} in order: {}", itemId, newQuantity, orderId);

        if (newQuantity < 1) {
            return removeItemFromOrder(orderId, itemId);
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        validateOrderCanBeModified(order);

        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Item not found with ID: " + itemId));

        int quantityDiff = newQuantity - item.getQuantity();

        if (quantityDiff > 0) {
            List<String> missingIngredients = inventoryService.getMissingIngredients(
                    item.getProductId(), quantityDiff);
            if (!missingIngredients.isEmpty()) {
                throw new IllegalStateException("Insufficient inventory: " + String.join("; ", missingIngredients));
            }
        }

        item.setQuantity(newQuantity);
        item.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(newQuantity)));

        recalculateOrderTotals(order);

        Order savedOrder = orderRepository.save(order);
        log.info("Item {} quantity updated to {} in order: {}", itemId, newQuantity, orderId);

        return savedOrder;
    }

    /**
     * Create an order item from the request
     */
    private OrderItem createOrderItem(Order order, Product product, ModifyOrderItemRequest request) {
        OrderItem newItem = new OrderItem();
        newItem.setOrder(order);
        newItem.setProductId(product.getId());
        newItem.setProductName(product.getName());
        newItem.setQuantity(request.getQuantity());

        BigDecimal price = request.getPrice() != null ? request.getPrice() : product.getPrice();
        newItem.setUnitPrice(price);
        newItem.setTotalPrice(price.multiply(BigDecimal.valueOf(request.getQuantity())));
        newItem.setSpecialInstructions(request.getNotes());

        // Add modifiers using the new structured relationship
        if (request.getModifiers() != null && !request.getModifiers().isEmpty()) {
            for (ModifyOrderItemRequest.ModifierInfo modifier : request.getModifiers()) {
                newItem.addAddOn(
                        modifier.getAddOnId(),
                        modifier.getName(),
                        modifier.getPrice() != null ? modifier.getPrice() : BigDecimal.ZERO,
                        modifier.getQuantity() != null ? modifier.getQuantity() : 1
                );
            }
            // Also set deprecated field for backward compatibility during transition
            String modifiersString = request.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice() != null && m.getPrice().compareTo(BigDecimal.ZERO) > 0 ?
                            " (+$" + m.getPrice() + ")" : ""))
                    .collect(Collectors.joining(", "));
            newItem.setAddOns(modifiersString);

            // Add modifier prices to item total
            BigDecimal modifierTotal = request.getModifiers().stream()
                    .filter(m -> m.getPrice() != null)
                    .map(ModifyOrderItemRequest.ModifierInfo::getPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            newItem.setTotalPrice(newItem.getTotalPrice().add(modifierTotal.multiply(BigDecimal.valueOf(request.getQuantity()))));
        }

        return newItem;
    }

    /**
     * Validate that the order can be modified
     */
    private void validateOrderCanBeModified(Order order) {
        if (!canModifyOrder(order)) {
            throw new IllegalStateException("Order cannot be modified in status: " + order.getStatus());
        }
    }

    /**
     * Check if order can be modified based on its status
     */
    public boolean canModifyOrder(Order order) {
        return order.getStatus() == OrderStatus.NEW ||
                order.getStatus() == OrderStatus.PENDING ||
                order.getStatus() == OrderStatus.ACCEPTED ||
                order.getStatus() == OrderStatus.PREPARING;
    }

    /**
     * Recalculate order totals after item changes
     */
    public void recalculateOrderTotals(Order order) {
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setSubtotal(subtotal);
        order.setTax(BigDecimal.ZERO);

        // Recalculate service fee if percent is set
        if (order.getServiceFeePercent() != null && order.getServiceFeePercent().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal serviceFee = subtotal.multiply(order.getServiceFeePercent())
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
            order.setServiceFee(serviceFee);
        }

        BigDecimal serviceFee = order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO;
        BigDecimal entryFee = order.getEntryFee() != null ? order.getEntryFee() : BigDecimal.ZERO;
        order.setTotal(subtotal.add(order.getTax()).add(order.getDeliveryFee()).add(serviceFee).add(entryFee));
    }
}
