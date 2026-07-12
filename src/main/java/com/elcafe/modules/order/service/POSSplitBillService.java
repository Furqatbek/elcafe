package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.order.dto.pos.SplitBillDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for split bill operations.
 * Handles splitting orders by items, evenly, or by custom amounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class POSSplitBillService {

    private final OrderRepository orderRepository;

    /**
     * Split an order's bill
     */
    @Transactional
    public SplitBillDTO.SplitBillResponse splitBill(Long orderId, SplitBillDTO request) {
        log.info("Splitting bill for order: {} mode: {}", orderId, request.getMode());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        return switch (request.getMode()) {
            case ITEMS -> splitByItems(order, request.getItemSplits());
            case EVEN -> splitEvenly(order, request.getNumPeople());
            case AMOUNT -> splitByAmount(order, request.getAmountSplits());
        };
    }

    /**
     * Split bill by items - each person pays for specific items
     */
    private SplitBillDTO.SplitBillResponse splitByItems(Order order, List<SplitBillDTO.ItemSplit> itemSplits) {
        List<SplitBillDTO.BillSplit> splits = new ArrayList<>();

        for (SplitBillDTO.ItemSplit itemSplit : itemSplits) {
            List<SplitBillDTO.SplitItemInfo> splitItems = new ArrayList<>();
            BigDecimal splitTotal = BigDecimal.ZERO;

            for (Long itemId : itemSplit.getItemIds()) {
                OrderItem item = order.getItems().stream()
                        .filter(i -> i.getId().equals(itemId))
                        .findFirst()
                        .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemId));

                splitItems.add(SplitBillDTO.SplitItemInfo.builder()
                        .itemId(item.getId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getTotalPrice())
                        .build());

                splitTotal = splitTotal.add(item.getTotalPrice());
            }

            splits.add(SplitBillDTO.BillSplit.builder()
                    .personNumber(itemSplit.getPersonNumber())
                    .amount(splitTotal)
                    .items(splitItems)
                    .paid(false)
                    .build());
        }

        return SplitBillDTO.SplitBillResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .mode(SplitBillDTO.SplitMode.ITEMS)
                .originalTotal(order.getTotal())
                .splits(splits)
                .build();
    }

    /**
     * Split bill evenly among a number of people
     */
    private SplitBillDTO.SplitBillResponse splitEvenly(Order order, Integer numPeople) {
        if (numPeople == null || numPeople < 2) {
            throw new BadRequestException("Number of people must be at least 2");
        }

        BigDecimal amountPerPerson = order.getTotal().divide(
                BigDecimal.valueOf(numPeople), 2, java.math.RoundingMode.HALF_UP);

        // Handle rounding - last person pays any remainder
        BigDecimal remainder = order.getTotal().subtract(
                amountPerPerson.multiply(BigDecimal.valueOf(numPeople)));

        List<SplitBillDTO.BillSplit> splits = new ArrayList<>();
        for (int i = 1; i <= numPeople; i++) {
            BigDecimal amount = amountPerPerson;
            if (i == numPeople && remainder.compareTo(BigDecimal.ZERO) != 0) {
                amount = amount.add(remainder);
            }

            splits.add(SplitBillDTO.BillSplit.builder()
                    .personNumber(i)
                    .amount(amount)
                    .items(Collections.emptyList())
                    .paid(false)
                    .build());
        }

        return SplitBillDTO.SplitBillResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .mode(SplitBillDTO.SplitMode.EVEN)
                .originalTotal(order.getTotal())
                .splits(splits)
                .build();
    }

    /**
     * Split bill by custom amounts
     */
    private SplitBillDTO.SplitBillResponse splitByAmount(Order order, List<SplitBillDTO.AmountSplit> amountSplits) {
        // Validate total matches
        BigDecimal totalSplitAmount = amountSplits.stream()
                .map(SplitBillDTO.AmountSplit::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalSplitAmount.compareTo(order.getTotal()) != 0) {
            throw new BadRequestException("Split amounts (" + totalSplitAmount +
                    ") do not match order total (" + order.getTotal() + ")");
        }

        List<SplitBillDTO.BillSplit> splits = amountSplits.stream()
                .map(as -> SplitBillDTO.BillSplit.builder()
                        .personNumber(as.getPersonNumber())
                        .amount(as.getAmount())
                        .items(Collections.emptyList())
                        .paid(false)
                        .build())
                .collect(Collectors.toList());

        return SplitBillDTO.SplitBillResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .mode(SplitBillDTO.SplitMode.AMOUNT)
                .originalTotal(order.getTotal())
                .splits(splits)
                .build();
    }
}
